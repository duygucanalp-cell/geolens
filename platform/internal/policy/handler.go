package policy

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

type Handler struct {
	pool dbiface.DB
}

func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

func NewProductionHandler(pool *db.Pool) *Handler {
	return NewHandler(dbiface.NewAdapter(pool))
}

type Pack struct {
	ID          string  `json:"id"`
	TenantID    string  `json:"tenant_id"`
	Name        string  `json:"name"`
	Framework   string  `json:"framework"`
	Description string  `json:"description"`
	Version     string  `json:"version"`
	Enabled     bool    `json:"enabled"`
	AppliedAt   *string `json:"applied_at,omitempty"`
	CreatedAt   string  `json:"created_at"`
	UpdatedAt   string  `json:"updated_at"`
}

type Control struct {
	ID          string  `json:"id"`
	PackID      string  `json:"pack_id"`
	TenantID    string  `json:"tenant_id"`
	ControlID   string  `json:"control_id"`
	Title       string  `json:"title"`
	Description string  `json:"description"`
	Category    string  `json:"category"`
	Status      string  `json:"status"`
	Evidence    string  `json:"evidence"`
	DueDate     *string `json:"due_date,omitempty"`
	CreatedAt   string  `json:"created_at"`
	UpdatedAt   string  `json:"updated_at"`
}

// SeedDefaultPacks creates default policy packs for a tenant if they don't exist.
// EU AI Act, NIST AI RMF, KVKK, ISO 42001 otomatik oluşturulur.
func SeedDefaultPacks(ctx context.Context, pool dbiface.DB, tenantID string) {
	frameworks := []struct {
		Name        string
		Framework   string
		Description string
	}{
		{"EU AI Act Compliance", "eu_ai_act", "Avrupa Birliği Yapay Zeka Yasası uyum paketi"},
		{"NIST AI RMF", "nist_ai_rmf", "NIST AI Risk Management Framework uyum paketi"},
		{"KVKK Uyum Paketi", "kvkk", "Kişisel Verilerin Korunması Kanunu uyum paketi"},
		{"ISO 42001 AI Management", "iso_42001", "ISO 42001 Yapay Zeka Yönetim Sistemi uyum paketi"},
	}

	for _, f := range frameworks {
		var packID string
		err := pool.QueryRow(ctx, `
			INSERT INTO policy.packs (tenant_id, name, framework, description, enabled)
			VALUES ($1, $2, $3, $4, true)
			ON CONFLICT (tenant_id, framework) DO UPDATE SET updated_at = now()
			RETURNING id
		`, tenantID, f.Name, f.Framework, f.Description).Scan(&packID)
		if err != nil {
			slog.Warn("policy pack seed hatası", "framework", f.Framework, "error", err)
			continue
		}

		// Seed default controls
		controls := frameworkControls(f.Framework)
		for _, c := range controls {
			_, err := pool.Exec(ctx, `
				INSERT INTO policy.controls (pack_id, tenant_id, control_id, title, description, category)
				VALUES ($1, $2, $3, $4, $5, $6)
				ON CONFLICT (pack_id, control_id) DO NOTHING
			`, packID, tenantID, c.ID, c.Title, c.Description, c.Category)
			if err != nil {
				slog.Error("control seed hatası", "error", err)
			}
		}

		slog.Info("policy pack seeded", "framework", f.Framework, "pack_id", packID, "controls", len(controls))
	}
}

func (h *Handler) ListPacks(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, tenant_id, name, framework, description, version, enabled, applied_at, created_at, updated_at
		FROM policy.packs WHERE tenant_id = $1 ORDER BY framework
	`, tenantID)
	if err != nil {
		slog.Error("policy pack sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"packs": []interface{}{}})
		return
	}
	defer rows.Close()

	var packs []Pack
	for rows.Next() {
		var p Pack
		if err := rows.Scan(&p.ID, &p.TenantID, &p.Name, &p.Framework, &p.Description,
			&p.Version, &p.Enabled, &p.AppliedAt, &p.CreatedAt, &p.UpdatedAt); err != nil {
			slog.Error("pack okuma hatası", "error", err)
			continue
		}
		packs = append(packs, p)
	}

	// Auto-seed: tenant hiç pack oluşturmamışsa default pack'leri yükle
	if len(packs) == 0 {
		SeedDefaultPacks(r.Context(), h.pool, tenantID)
		// Yeniden sorgula
		rows2, err2 := h.pool.Query(r.Context(), `
			SELECT id, tenant_id, name, framework, description, version, enabled, applied_at, created_at, updated_at
			FROM policy.packs WHERE tenant_id = $1 ORDER BY framework
		`, tenantID)
		if err2 == nil {
			defer rows2.Close()
			packs = nil
			for rows2.Next() {
				var p Pack
				if err := rows2.Scan(&p.ID, &p.TenantID, &p.Name, &p.Framework, &p.Description,
					&p.Version, &p.Enabled, &p.AppliedAt, &p.CreatedAt, &p.UpdatedAt); err != nil {
					slog.Warn("policy pack (seed tekrar) satır okuma hatası", "error", err)
					continue
				}
				packs = append(packs, p)
			}
			if rows2.Err() != nil {
				slog.Warn("policy pack (seed tekrar) rows iterasyon hatası", "error", rows2.Err())
			}
		}
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"packs": packs})
}

func (h *Handler) ApplyPack(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	packID := chi.URLParam(r, "packId")

	var p Pack
	err := h.pool.QueryRow(r.Context(), `
		UPDATE policy.packs SET enabled = true, applied_at = now(), updated_at = now()
		WHERE id = $1 AND tenant_id = $2
		RETURNING id, tenant_id, name, framework, description, version, enabled, applied_at, created_at, updated_at
	`, packID, tenantID).Scan(
		&p.ID, &p.TenantID, &p.Name, &p.Framework, &p.Description,
		&p.Version, &p.Enabled, &p.AppliedAt, &p.CreatedAt, &p.UpdatedAt)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "pack bulunamadı"})
		return
	}

	// Seed default controls if not exist
	h.seedControls(r.Context(), p.ID, tenantID, p.Framework)

	httputil.WriteJSON(w, http.StatusOK, p)
}

func (h *Handler) GetCompliance(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	entityID := chi.URLParam(r, "entityId")

	var total, passed, failed, notApplicable int
	_ = h.pool.QueryRow(r.Context(), `
		SELECT
			COUNT(*)::int,
			COALESCE(SUM(CASE WHEN status = 'passed' THEN 1 ELSE 0 END), 0)::int,
			COALESCE(SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END), 0)::int,
			COALESCE(SUM(CASE WHEN status = 'not_applicable' THEN 1 ELSE 0 END), 0)::int
		FROM policy.controls WHERE tenant_id = $1
	`, tenantID).Scan(&total, &passed, &failed, &notApplicable)

	// entity_id varsa registry risk assessment ile ilişkilendir
	var riskLevel string
	if entityID != "" && entityID != "undefined" && entityID != "null" {
		_ = h.pool.QueryRow(r.Context(), `
			SELECT COALESCE(risk_class, '') FROM registry.entities WHERE id = $1 AND tenant_id = $2
		`, entityID, tenantID).Scan(&riskLevel)
	}

	compliancePct := float64(0)
	if total > 0 {
		compliancePct = float64(passed) / float64(total) * 100
	}

	result := map[string]interface{}{
		"entity_id":      entityID,
		"compliance_pct": compliancePct,
		"total_controls": total,
		"passed":         passed,
		"failed":         failed,
		"not_applicable": notApplicable,
	}
	if riskLevel != "" {
		result["entity_risk_class"] = riskLevel
	}

	httputil.WriteJSON(w, http.StatusOK, result)
}

func (h *Handler) seedControls(ctx context.Context, packID, tenantID, framework string) {
	controls := frameworkControls(framework)
	for _, c := range controls {
		_, err := h.pool.Exec(ctx, `
			INSERT INTO policy.controls (pack_id, tenant_id, control_id, title, description, category)
			VALUES ($1, $2, $3, $4, $5, $6)
			ON CONFLICT (pack_id, control_id) DO NOTHING
		`, packID, tenantID, c.ID, c.Title, c.Description, c.Category)
		if err != nil {
			slog.Error("control seed hatası", "error", err)
		}
	}
}

type controlDef struct {
	ID          string
	Title       string
	Description string
	Category    string
}

func frameworkControls(framework string) []controlDef {
	switch framework {
	case "eu_ai_act":
		return []controlDef{
			{"Art.9", "Risk Yönetim Sistemi", "Sürekli, yinelemeli risk yönetim süreci", "Risk Management"},
			{"Art.10", "Eğitim Verisi Yönetimi", "Eğitim verisi kalitesi, bias analizi, temsiliyet", "Data Governance"},
			{"Art.11", "Teknik Dokümantasyon", "Model mimarisi, eğitim yöntemi, performans metrikleri", "Documentation"},
			{"Art.12", "Kayıt Tutma", "Olay günlükleri, otomatik loglama, saklama süresi", "Monitoring"},
			{"Art.13", "Şeffaflık ve Bilgilendirme", "Kullanıcılara AI sistemi bildirimi, açıklanabilirlik", "Transparency"},
			{"Art.14", "İnsan Gözetimi", "İnsan müdahale mekanizmaları, override yetkisi", "Oversight"},
			{"Art.15", "Doğruluk ve Dayanıklılık", "Doğruluk metrikleri, hata toleransı, güvenilirlik", "Performance"},
		}
	case "nist_ai_rmf":
		return []controlDef{
			{"GOV-1", "Yönetişim Yapısı", "AI risk yönetimi için organizasyonel yapı", "Govern"},
			{"GOV-2", "Politika ve Prosedürler", "AI kullanım politikaları, etik kurallar", "Govern"},
			{"MAP-1", "AI Sistemi Envanteri", "Tüm AI sistemlerinin tanımlanması ve sınıflandırılması", "Map"},
			{"MAP-2", "Risk Değerlendirmesi", "AI sistemlerinin risk seviyesinin belirlenmesi", "Map"},
			{"MEA-1", "Performans İzleme", "Sürekli model performans ve drift izleme", "Measure"},
			{"MEA-2", "Bias ve Adillik", "Demoğrafik parite, eşitlik metrikleri", "Measure"},
			{"MAN-1", "Risk Azaltma", "Tesbit edilen risklerin azaltılması ve yönetimi", "Manage"},
		}
	case "kvkk":
		return []controlDef{
			{"KVKK-4", "Açık Rıza", "Veri sahibinin açık rızasının alınması", "Consent"},
			{"KVKK-5", "Veri Envanteri", "Kişisel veri işleme envanteri", "Data Inventory"},
			{"KVKK-6", "Aydınlatma Yükümlülüğü", "Veri sahibinin bilgilendirilmesi", "Transparency"},
			{"KVKK-7", "Veri Güvenliği", "Teknik ve idari tedbirler, şifreleme", "Security"},
			{"KVKK-8", "Silme ve Yok Etme", "Veri saklama süreleri, periyodik imha", "Retention"},
			{"KVKK-9", "Veri Sorumlusu Kayıt", "VERBIS kaydı ve güncellemesi", "Compliance"},
		}
	case "iso_42001":
		return []controlDef{
			{"6.1", "Risk Değerlendirmesi", "AI risk değerlendirme ve tedavi planı", "Planning"},
			{"7.2", "Yetkinlik", "AI personel yetkinlik ve eğitim gereksinimleri", "Support"},
			{"7.4", "İletişim", "AI sistemi kullanımı hakkında paydaş iletişimi", "Support"},
			{"8.1", "Operasyonel Planlama", "AI sistemi geliştirme ve işletme kontrolleri", "Operation"},
			{"8.2", "AI Sistem Değerlendirmesi", "AI sistem etki değerlendirmesi", "Operation"},
			{"9.1", "Performans İzleme", "AI sistemi performans ve uygunluk izleme", "Evaluation"},
		}
	default: // custom
		return []controlDef{
			{"C001", "Özel Kontrol 1", "Tanımlanmış özel kontrol", "Custom"},
		}
	}
}

func (h *Handler) ListControls(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	packID := chi.URLParam(r, "packId")

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, pack_id, tenant_id, control_id, title, description, category, status, evidence, due_date, created_at, updated_at
		FROM policy.controls WHERE pack_id = $1 AND tenant_id = $2 ORDER BY control_id
	`, packID, tenantID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"controls": []interface{}{}})
		return
	}
	defer rows.Close()

	var controls []Control
	for rows.Next() {
		var c Control
		if err := rows.Scan(&c.ID, &c.PackID, &c.TenantID, &c.ControlID, &c.Title,
			&c.Description, &c.Category, &c.Status, &c.Evidence, &c.DueDate, &c.CreatedAt, &c.UpdatedAt); err != nil {
			slog.Warn("policy control satır okuma hatası", "error", err)
			continue
		}
		controls = append(controls, c)
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"controls": controls})
}

func (h *Handler) SeedPacks(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	SeedDefaultPacks(r.Context(), h.pool, tenantID)
	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "policy packs seeded"})
}

func (h *Handler) UpdateControl(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	controlID := chi.URLParam(r, "controlId")

	var input struct {
		Status   string `json:"status"`
		Evidence string `json:"evidence"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	_, err := h.pool.Exec(r.Context(), `
		UPDATE policy.controls SET status = $1, evidence = $2, updated_at = now()
		WHERE id = $3 AND tenant_id = $4
	`, input.Status, input.Evidence, controlID, tenantID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "control güncellenemedi"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "güncellendi"})
}
