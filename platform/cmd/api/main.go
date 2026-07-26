package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/joho/godotenv"
	"github.com/prometheus/client_golang/prometheus/promhttp"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/engine/chatgpt"
	"github.com/geolens/platform/engine/claude"
	"github.com/geolens/platform/engine/gemini"
	"github.com/geolens/platform/engine/perplexity"
	"github.com/geolens/platform/internal/alert"
	"github.com/geolens/platform/internal/apikey"
	"github.com/geolens/platform/internal/audit"
	"github.com/geolens/platform/internal/auth"
	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/internal/delivery"
	"github.com/geolens/platform/internal/governance"
	"github.com/geolens/platform/internal/measure"
	"github.com/geolens/platform/internal/pdf"
	"github.com/geolens/platform/internal/privacy"
	"github.com/geolens/platform/internal/public"
	"github.com/geolens/platform/internal/recommendation"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/queue"
	"github.com/geolens/platform/platform/storage"
	"github.com/geolens/platform/platform/telemetry"
)

func main() {
	// .env yükle (varsa)
	_ = godotenv.Load()

	// Yapılandırma
	cfg := config.LoadFromEnv()

	// Logger
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: cfg.LogLevel})))

	// OpenTelemetry
	shutdown, err := telemetry.InitOTel(context.Background(), cfg)
	if err != nil {
		slog.Error("opentelemetry başlatılamadı", "error", err)
		os.Exit(1)
	}
	defer shutdown()

	// PostgreSQL havuzu
	pool, err := db.NewPool(context.Background(), cfg.DatabaseURL)
	if err != nil {
		slog.Error("veritabanı bağlantısı kurulamadı", "error", err)
		os.Exit(1)
	}
	defer pool.Close()

	// JWT servisi
	jwtService := auth.NewJWTService(cfg.JWTSecret)

	// S3 Storage (MinIO)
	s3Client, err := storage.NewClient(cfg.S3Endpoint, cfg.S3AccessKey, cfg.S3SecretKey, cfg.S3Bucket, cfg.S3Region, false)
	if err != nil {
		slog.Warn("S3 istemci oluşturulamadı, storage olmadan çalışılacak", "error", err)
	}

	// Ortak RawSaver: nil-hatasız storage backend
	// Crypto-shredding: STORAGE_MASTER_KEY varsa EncryptedClient, yoksa plain Client kullan
	var saver engine.RawSaver
	if err == nil {
		if cfg.StorageMasterKey != "" {
			encClient, encryptErr := storage.NewEncryptedClient(s3Client, cfg.StorageMasterKey)
			if encryptErr != nil {
				slog.Warn("EncryptedClient oluşturulamadı, şifresiz storage kullanılacak", "error", encryptErr)
				saver = s3Client
			} else {
				saver = encClient
				slog.Info("kripto-silme etkin: S3 verileri AES-256-GCM şifreli")
			}
		} else {
			saver = s3Client
		}
	}

	// Engine registry
	engines := engine.NewRegistry()

	// Perplexity (Kademe 1)
	perplexityAdapter := perplexity.NewAdapter(cfg.PerplexityAPIKey, saver)
	engines.Register(perplexityAdapter)

	// ChatGPT / OpenAI (Kademe 1)
	chatgptAdapter := chatgpt.NewAdapter(cfg.ChatGPTAPIKey, saver)
	engines.Register(chatgptAdapter)

	// Gemini / Google AI (Kademe 1)
	geminiAdapter := gemini.NewAdapter(cfg.GeminiAPIKey, saver)
	engines.Register(geminiAdapter)

	// Claude / Anthropic (Kademe 2)
	claudeAdapter := claude.NewAdapter(cfg.ClaudeAPIKey, saver)
	engines.Register(claudeAdapter)

	slog.Info("motor kayıt defteri hazır", "engine_count", engines.Count(), "engines", engines.List())

	// Rate limit quota checker
	quotaChecker := governance.NewQuotaChecker(pool)

	// Redis client (queue ile aynı — mevcut bağlantıyı kullan)
	redisClient, err := queue.NewRedisClient(cfg.RedisURL)
	if err != nil {
		slog.Warn("redis istemci oluşturulamadı, cache olmadan çalışılacak", "error", err)
	}
	if redisClient != nil {
		defer redisClient.Close()
	}

	// Handler'lar
	authHandler := auth.NewHandler(pool, jwtService, redisClient)
	configHandler := config.NewHandler(pool)
	panelHandler := config.NewPanelHandler(pool)
	measureHandler := measure.NewHandler(pool, engines)
	auditHandler := audit.NewHandler(pool)
	deliveryHandler := delivery.NewHandler(pool, delivery.EmailConfig{
		FromName:    cfg.SendGridFromName,
		FromEmail:   cfg.SendGridFromEmail,
		SendGridKey: cfg.SendGridAPIKey,
	})
	privacyHandler := privacy.NewHandler(pool)
	recommendationHandler := recommendation.NewHandler(pool)
	pdfHandler := pdf.NewHandler(pool)
	alertHandler := alert.NewHandler(pool)
	apiKeyHandler := apikey.NewHandler(pool)
	publicHandler := public.NewHandler(pool)

	// Async rapor işleyiciyi başlat (10 saniyede bir poll)
	pdf.StartReportProcessor(pool, pdfHandler.Svc(), 10*time.Second)

	// Router
	r := chi.NewRouter()

	// Global middleware zinciri (sabit sıra — 0501 §5)
	r.Use(httpmw.PanicRecovery)
	r.Use(httpmw.RequestID)
	r.Use(httpmw.SecureHeaders)
	r.Use(httpmw.RequestTimeout(30 * time.Second))
	r.Use(httpmw.MaxBodySize(1 << 20)) // 1MB max body
	r.Use(middleware.RealIP)
	r.Use(httpmw.MetricsMiddleware) // H14: Prometheus metrikleri
	r.Use(httpmw.Logger)
	r.Use(httpmw.CORS)

	// Health check (auth'suz)
	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status":"ok"}`))
	})

	// Prometheus metrics endpoint (auth'suz, herkes erişebilir)
	r.Get("/metrics", promhttp.Handler().ServeHTTP)

	// Public API v1 (API key auth, FR-F6)
	r.Route("/public/v1", func(r chi.Router) {
		r.Use(httpmw.AuthenticateAPIKey(pool))
		r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/scores/{brandID}", publicHandler.GetScore)
		r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/trends", publicHandler.ListTrends)
	})

	// API v1
	r.Route("/v1", func(r chi.Router) {
		// JSON body validation for all POST/PUT/PATCH routes
		r.Use(httpmw.ValidateContentType("application/json"))

		// Public auth routes (JWT gerekmez)
		r.Post("/auth/register", authHandler.Register)
		r.Post("/auth/login", authHandler.Login)
		r.Post("/auth/accept-invitation", authHandler.AcceptInvitation)

		// Protected routes (JWT gerekli)
		r.Group(func(r chi.Router) {
			r.Use(httpmw.Authenticate(jwtService.TokenValidator(redisClient)))
			r.Use(httpmw.TenantContext(pool))
			r.Use(httpmw.RateLimit(quotaChecker, "api_requests_per_hour"))

			// Auth-protected utilities
			r.Post("/auth/logout", authHandler.Logout)

			// Tenant member management
			r.Get("/tenant", authHandler.GetTenant)
			r.Get("/tenant/members", authHandler.ListMembers)
			r.Patch("/tenant/members/{userId}/role", authHandler.UpdateMemberRole)
			r.Get("/tenant/invitations", authHandler.ListInvitations)
			// Pro+ gated: invitation endpoint requires paid tier
			r.With(httpmw.RequireTier(pool, httpmw.TierPro)).Post("/tenant/invitations", authHandler.InviteMember)

			// KVKK/GDPR account deletion (tenant-level, workspace gerektirmez)
			r.Post("/account/deletion", privacyHandler.RequestDeletion)
			r.Post("/privacy/delete", privacyHandler.RequestDeletion) // ADR uyumu alias
			r.Get("/deletion-requests", privacyHandler.ListDeletionRequests)
			r.Post("/deletion-requests/{id}/process", privacyHandler.ProcessDeletionRequest)

			// H5: Multi-customer panorama (tenant-level)
			r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/tenant/panorama", configHandler.ListWorkspacePanorama)

			// H1: API key management
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/api-keys", apiKeyHandler.List)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Post("/api-keys", apiKeyHandler.Create)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Delete("/api-keys/{keyId}", apiKeyHandler.Delete)

			// T3: Audit trail (admin)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/admin/audit-trail", auditHandler.ListAuditTrail)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/admin/audit-trail/export", auditHandler.ExportAuditTrail)

			// Workspace-scoped routes (auth + workspace membership gerekli)
			r.Route("/workspaces/{ws}", func(r chi.Router) {
				r.Use(httpmw.RequireWorkspace)
				r.Use(httpmw.RequireWorkspaceAccess(pool))

				// Cache middleware (GET endpoint'leri için Redis önbellek)
				var cacheCfg httpmw.CacheConfig
				if redisClient != nil {
					cacheCfg = httpmw.CacheConfig{RDB: redisClient, TTL: 30 * time.Second}
				}

				// Viewer+ (GET routes — okuma erişimi)
				r.Group(func(r chi.Router) {
					r.Use(httpmw.CacheMiddleware(cacheCfg))
					r.Use(httpmw.RequireRole(httpmw.RoleViewer))
					r.Get("/brands", configHandler.ListBrands)
					r.Get("/panels", panelHandler.ListPanels)
					r.Get("/panels/{panelID}", panelHandler.GetPanel)
					r.Get("/prompt-sets", panelHandler.ListPromptSets)
					r.Get("/scores", measureHandler.ListScores)
					r.Get("/trends", measureHandler.ListTrends)
					r.Get("/brands/{brandID}/scores", measureHandler.ListBrandScores)
					r.Get("/notifications/settings", deliveryHandler.GetSettings)
					r.Get("/recommendations", recommendationHandler.ListRecommendations)
				})

				// Admin-only routes
				r.Group(func(r chi.Router) {
					r.Use(httpmw.RequireRole(httpmw.RoleAdmin))
					r.Post("/brands", configHandler.CreateBrand)
					// H4: Archive/transfer
					r.Post("/archive", configHandler.ArchiveWorkspace)
					r.Post("/unarchive", configHandler.UnarchiveWorkspace)
					r.Post("/transfer", configHandler.TransferWorkspace)
				})

				// Viewer+ routes (setup-status, citations, benchmark, alert-rules, report status, impact, radar, findings)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/setup-status", configHandler.GetSetupStatus)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/citations", measureHandler.ListCitations)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/benchmark", measureHandler.ListBenchmark)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/alert-rules", alertHandler.List)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/reports/{reportId}/status", pdfHandler.GetReportStatus)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/reports/{reportId}/download", pdfHandler.DownloadReport)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/recommendations/{recId}/impact", recommendationHandler.GetImpact)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/radar", measureHandler.ListRadarComparison)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/audit/findings", auditHandler.GetFindingsCatalog)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/recommendations/rules", recommendationHandler.ListRules)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/recommendations/rules/{sector}", recommendationHandler.ListRulesBySector)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/benchmark/context", measureHandler.GetBenchmarkContext)
				// X9: Measurement status
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/measurements/{runId}/status", measureHandler.GetMeasurementStatus)

				// Editor+ routes (yazma/aksiyon işlemleri)
				r.Group(func(r chi.Router) {
					r.Use(httpmw.RequireRole(httpmw.RoleEditor))
					r.Post("/panels", panelHandler.CreatePanel)
					r.Post("/prompt-sets", panelHandler.CreatePromptSet)
					r.Post("/measurements", measureHandler.TriggerMeasurement)
					r.Post("/audit", auditHandler.RunAudit)
					r.Put("/notifications/settings", deliveryHandler.UpdateSettings)
					r.Post("/notifications/test", deliveryHandler.SendTestEmail)
					r.Post("/recommendations/{recId}/apply", recommendationHandler.MarkApplied)
					r.Post("/recommendations/{recId}/dismiss", recommendationHandler.MarkDismissed)

					// Async report request (FR-F5)
					r.Post("/reports", pdfHandler.RequestReport)

					// Pro+ features: sync reports
					r.Group(func(r chi.Router) {
						r.Use(httpmw.RequireTier(pool, httpmw.TierPro))
						r.Post("/reports/digest", pdfHandler.GenerateWeeklyDigest)
						r.Post("/reports/score-card", pdfHandler.GenerateScoreCard)
						r.Post("/reports/audit", pdfHandler.GenerateAuditReport)
					})

					// Alert rules CRUD (editor, marka bazlı uyarı ayarları)
					r.Post("/alert-rules", alertHandler.Create)
					r.Put("/alert-rules/{ruleId}", alertHandler.Update)
					r.Delete("/alert-rules/{ruleId}", alertHandler.Delete)
				})

			})
		})
	})

	// HTTP sunucusu
	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      r,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Graceful shutdown
	go func() {
		slog.Info("api sunucusu başlatılıyor", "port", cfg.Port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("sunucu hatası", "error", err)
			os.Exit(1)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	slog.Info("sunucu kapatılıyor...")
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		slog.Error("sunucu kapatılamadı", "error", err)
		os.Exit(1)
	}
}
