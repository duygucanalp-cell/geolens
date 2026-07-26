package policy

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

type Handler struct {
	pool *db.Pool
}

func NewHandler(pool *db.Pool) *Handler {
	return &Handler{pool: pool}
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
	h.seedControls(p.ID, tenantID, p.Framework)

	httputil.WriteJSON(w, http.StatusOK, p)
}

func (h *Handler) GetCompliance(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	entityID := chi.URLParam(r, "entityId")

	var total, passed, failed int
	_ = h.pool.QueryRow(r.Context(), `
		SELECT COUNT(*), COALESCE(SUM(CASE WHEN status = 'passed' THEN 1 ELSE 0 END), 0),
			COALESCE(SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END), 0)
		FROM policy.controls WHERE tenant_id = $1
	`, tenantID).Scan(&total, &passed, &failed)

	compliancePct := float64(0)
	if total > 0 {
		compliancePct = float64(passed) / float64(total) * 100
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"entity_id":      entityID,
		"compliance_pct": compliancePct,
		"total_controls": total,
		"passed":         passed,
		"failed":         failed,
		"not_applicable": total - passed - failed,
	})
}

func (h *Handler) seedControls(packID, tenantID, framework string) {
	controls := frameworkControls(framework)
	for _, c := range controls {
		_, err := h.pool.Exec(nil, `
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
			continue
		}
		controls = append(controls, c)
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"controls": controls})
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
