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
	"github.com/geolens/platform/engine/copilot"
	"github.com/geolens/platform/engine/gemini"
	"github.com/geolens/platform/engine/grok"
	"github.com/geolens/platform/engine/perplexity"
	"github.com/geolens/platform/internal/agent"
	"github.com/geolens/platform/internal/alert"
	"github.com/geolens/platform/internal/apikey"
	"github.com/geolens/platform/internal/audit"
	"github.com/geolens/platform/internal/auth"
	"github.com/geolens/platform/internal/bias"
	"github.com/geolens/platform/internal/billing"
	"github.com/geolens/platform/internal/compliance"
	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/internal/delivery"
	"github.com/geolens/platform/internal/discovery"
	"github.com/geolens/platform/internal/explain"
	"github.com/geolens/platform/internal/gate"
	"github.com/geolens/platform/internal/governance"
	"github.com/geolens/platform/internal/guardrail"
	"github.com/geolens/platform/internal/measure"
	"github.com/geolens/platform/internal/pdf"
	"github.com/geolens/platform/internal/pilot"
	"github.com/geolens/platform/internal/policy"
	"github.com/geolens/platform/internal/privacy"
	"github.com/geolens/platform/internal/public"
	"github.com/geolens/platform/internal/recommendation"
	"github.com/geolens/platform/internal/registry"
	"github.com/geolens/platform/internal/retention"
	"github.com/geolens/platform/internal/sso"
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

	// Grok / xAI (Kademe 2)
	grokAdapter := grok.NewAdapter(cfg.GrokAPIKey, saver)
	engines.Register(grokAdapter)

	// Copilot / Microsoft (Kademe 3)
	copilotAdapter := copilot.NewAdapter(cfg.CopilotAPIKey, saver)
	engines.Register(copilotAdapter)

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
	billingHandler := billing.NewHandler(pool, cfg.StripeAPIKey, cfg.StripeWebhookSecret)
	complianceHandler := compliance.NewHandler(pool)
	retentionHandler := retention.NewHandler(pool)
	pilotHandler := pilot.NewHandler(pool)
	ssoHandler := sso.NewHandler(pool)
	registryHandler := registry.NewHandler(pool)
	policyHandler := policy.NewHandler(pool)
	guardrailHandler := guardrail.NewHandler(pool)
	discoveryHandler := discovery.NewHandler(pool)
	biasHandler := bias.NewHandler(pool)
	gateHandler := gate.NewHandler(pool)
	explainHandler := explain.NewHandler(pool)
	agentHandler := agent.NewHandler(pool)

	// Async rapor işleyiciyi başlat (10 saniyede bir poll)
	pdf.StartReportProcessor(pool, pdfHandler.Svc(), 10*time.Second)

	// K3: Veri saklama işçisi (24 saatte bir)
	retentionWorker := retention.NewWorker(pool, cfg.RetentionInterval)
	go retentionWorker.Start(context.Background())

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

		// K1: SSO ACS endpoint — IdP'den gelen SAML yanıtını kabul eder (JWT gerekmez)
		r.Post("/sso/acs/{tenantId}", ssoHandler.HandleACS)

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

			// T1: Billing / self-serve upgrade (authenticated)
			r.Post("/billing/checkout", billingHandler.CreateCheckoutSession)
			r.Post("/billing/webhook", billingHandler.HandleWebhook)
			r.Get("/billing/subscription", billingHandler.GetSubscription)

			// K2: SOC 2 compliance / evidence (authenticated)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/compliance/soc2", complianceHandler.SOC2Readiness)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/compliance/report", complianceHandler.ComplianceReport)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/compliance/evidence", complianceHandler.ListEvidence)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/compliance/evidence/download", complianceHandler.DownloadEvidence)

			// K4: Pilot program (authenticated)
			r.Get("/pilot/status", pilotHandler.GetStatus)
			r.Post("/pilot/enroll", pilotHandler.Enroll)
			r.Post("/pilot/extend", pilotHandler.ExtendTrial)
			r.Post("/pilot/cancel", pilotHandler.Cancel)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/pilot/tenants", pilotHandler.ListAll)

			// K1: SSO/SAML (authenticated)
			r.Route("/sso", func(r chi.Router) {
				r.Use(httpmw.RequireRole(httpmw.RoleAdmin))
				r.Get("/config", ssoHandler.GetConfig)
				r.Put("/config", ssoHandler.UpdateConfig)
				r.Get("/metadata", ssoHandler.GetSPMetadata)
				r.Post("/enable", ssoHandler.Enable)
				r.Post("/disable", ssoHandler.Disable)
				r.Post("/generate-keys", ssoHandler.GenerateKeyPair)
			})

			// R1: AI Registry (authenticated)
			r.Route("/registry", func(r chi.Router) {
				r.Use(httpmw.RequireRole(httpmw.RoleViewer))
				r.Get("/entities", registryHandler.List)
				r.Get("/entities/{entityId}", registryHandler.Get)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/entities", registryHandler.Create)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Put("/entities/{entityId}", registryHandler.Update)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Delete("/entities/{entityId}", registryHandler.Delete)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/entities/{entityId}/assess", registryHandler.AssessRisk)
			})

			// R4: Policy Packs (authenticated)
			r.Route("/policies", func(r chi.Router) {
				r.Use(httpmw.RequireRole(httpmw.RoleViewer))
				r.Get("/packs", policyHandler.ListPacks)
				r.Get("/packs/{packId}/controls", policyHandler.ListControls)
				r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Post("/packs/{packId}/apply", policyHandler.ApplyPack)
				r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Put("/controls/{controlId}", policyHandler.UpdateControl)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/compliance/{entityId}", policyHandler.GetCompliance)
			})

			// R3: Runtime Guardrails (authenticated)
			r.Route("/guardrails", func(r chi.Router) {
				r.Use(httpmw.RequireRole(httpmw.RoleViewer))
				r.Get("/rules", guardrailHandler.ListRules)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/rules", guardrailHandler.CreateRule)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Delete("/rules/{ruleId}", guardrailHandler.DeleteRule)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/seed-defaults", guardrailHandler.SeedDefaults)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/evaluate", guardrailHandler.Evaluate)
			})

			// R2: Shadow AI Discovery (authenticated)
			r.Route("/discovery", func(r chi.Router) {
				r.Use(httpmw.RequireRole(httpmw.RoleAdmin))
				r.Post("/scan", discoveryHandler.StartScan)
				r.Get("/scans/{scanId}", discoveryHandler.GetScanResults)
			})

			// R5: Bias/Fairness (authenticated)
			r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/bias/evaluate", biasHandler.Evaluate)

			// R6: CI/CD Governance Gate (authenticated)
			r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/gate/check", gateHandler.Check)
			r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/gate/history/{entityId}", gateHandler.History)

			// R7: Explainability (authenticated)
			r.With(httpmw.RequireRole(httpmw.RoleViewer)).Post("/explain/{entityId}", explainHandler.Explain)

			// R8: Agent Tracing (authenticated)
			r.Route("/agents", func(r chi.Router) {
				r.Use(httpmw.RequireRole(httpmw.RoleViewer))
				r.Post("/traces", agentHandler.StartTrace)
				r.Get("/traces/{traceId}", agentHandler.GetTrace)
				r.Get("/traces", agentHandler.ListTraces)
			})

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

				// K3: Data retention (viewer — okuma, editor — yazma)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/retention/policies", retentionHandler.ListPolicies)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/retention/archive-summary", retentionHandler.GetArchiveSummary)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Put("/retention/policies", retentionHandler.UpsertPolicy)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Delete("/retention/policies/{policyId}", retentionHandler.DeletePolicy)

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
