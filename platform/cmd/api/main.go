// Package main is the entry point for the GeoLens API server.
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
	"github.com/geolens/platform/internal/benchmark"
	"github.com/geolens/platform/internal/bias"
	"github.com/geolens/platform/internal/billing"
	"github.com/geolens/platform/internal/compliance"
	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/internal/cost"
	"github.com/geolens/platform/internal/delivery"
	"github.com/geolens/platform/internal/discovery"
	"github.com/geolens/platform/internal/explain"
	"github.com/geolens/platform/internal/gate"
	"github.com/geolens/platform/internal/governance"
	"github.com/geolens/platform/internal/guardrail"
	"github.com/geolens/platform/internal/incident"
	"github.com/geolens/platform/internal/measure"
	"github.com/geolens/platform/internal/optimize"
	"github.com/geolens/platform/internal/pdf"
	"github.com/geolens/platform/internal/pilot"
	"github.com/geolens/platform/internal/policy"
	"github.com/geolens/platform/internal/privacy"
	"github.com/geolens/platform/internal/prompt"
	"github.com/geolens/platform/internal/public"
	"github.com/geolens/platform/internal/recommendation"
	"github.com/geolens/platform/internal/registry"
	"github.com/geolens/platform/internal/retention"
	"github.com/geolens/platform/internal/sso"
	"github.com/geolens/platform/internal/usage"
	"github.com/geolens/platform/internal/version"
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
		return
	}
	defer shutdown()

	// PostgreSQL havuzu
	pool, err := db.NewPool(context.Background(), cfg.DatabaseURL)
	if err != nil {
		slog.Error("veritabanı bağlantısı kurulamadı", "error", err)
		return
	}
	pool.Close()

	// JWT servisi
	jwtService := auth.NewJWTService(cfg.JWTSecret)

	// S3 Storage (MinIO)
	s3Client, err := storage.NewClient(cfg.S3Endpoint, cfg.S3AccessKey, cfg.S3SecretKey, cfg.S3Bucket, cfg.S3Region, false)
	if err != nil {
		slog.Warn("S3 istemci oluşturulamadı, storage olmadan çalışılacak", "error", err)
	}

	// Ortak RawSaver: nil-hatasız storage backend
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

	perplexityAdapter := perplexity.NewAdapter(cfg.PerplexityAPIKey, saver)
	engines.Register(perplexityAdapter)
	chatgptAdapter := chatgpt.NewAdapter(cfg.ChatGPTAPIKey, saver)
	engines.Register(chatgptAdapter)
	geminiAdapter := gemini.NewAdapter(cfg.GeminiAPIKey, saver)
	engines.Register(geminiAdapter)
	claudeAdapter := claude.NewAdapter(cfg.ClaudeAPIKey, saver)
	engines.Register(claudeAdapter)
	grokAdapter := grok.NewAdapter(cfg.GrokAPIKey, saver)
	engines.Register(grokAdapter)
	copilotAdapter := copilot.NewAdapter(cfg.CopilotAPIKey, saver)
	engines.Register(copilotAdapter)

	slog.Info("motor kayıt defteri hazır", "engine_count", engines.Count(), "engines", engines.List())

	quotaChecker := governance.NewQuotaChecker(pool)

	redisClient, err := queue.NewRedisClient(cfg.RedisURL)
	if err != nil {
		slog.Warn("redis istemci oluşturulamadı, cache olmadan çalışılacak", "error", err)
	}
	if redisClient != nil {
		defer func() { _ = redisClient.Close() }()
	}

	// Handler'lar
	authHandler := auth.NewProductionHandler(pool, jwtService, redisClient)
	configHandler := config.NewProductionHandler(pool)
	panelHandler := config.NewProductionPanelHandler(pool)
	measureHandler := measure.NewProductionHandler(pool, engines)
	auditHandler := audit.NewProductionHandler(pool)
	deliveryHandler := delivery.NewProductionHandler(pool, delivery.EmailConfig{
		FromName: cfg.SendGridFromName, FromEmail: cfg.SendGridFromEmail, SendGridKey: cfg.SendGridAPIKey,
	})
	privacyHandler := privacy.NewProductionHandler(pool)
	recommendationHandler := recommendation.NewProductionHandler(pool)
	pdfHandler := pdf.NewProductionHandler(pool)
	alertHandler := alert.NewProductionHandler(pool)
	apiKeyHandler := apikey.NewProductionHandler(pool)
	publicHandler := public.NewProductionHandler(pool)
	billingHandler := billing.NewHandler(pool, cfg.StripeAPIKey, cfg.StripeWebhookSecret)
	complianceHandler := compliance.NewHandler(pool)
	retentionHandler := retention.NewHandler(pool)
	pilotHandler := pilot.NewHandler(pool)
	ssoHandler := sso.NewHandler(pool)
	registryHandler := registry.NewProductionHandler(pool)
	policyHandler := policy.NewProductionHandler(pool)
	guardrailHandler := guardrail.NewProductionHandler(pool)
	discoveryHandler := discovery.NewProductionHandler(pool)
	biasHandler := bias.NewProductionHandler(pool)
	gateHandler := gate.NewProductionHandler(pool)
	explainHandler := explain.NewProductionHandler(pool)
	agentHandler := agent.NewProductionHandler(pool)
	promptHandler := prompt.NewProductionHandler(pool)
	benchmarkHandler := benchmark.NewProductionHandler(pool)
	costHandler := cost.NewProductionHandler(pool)
	usageHandler := usage.NewProductionHandler(pool)
	optimizeHandler := optimize.NewProductionHandler(pool)
	versionHandler := version.NewProductionHandler(pool)
	incidentHandler := incident.NewProductionHandler(pool)

	pdf.StartReportProcessor(pool, pdfHandler.Svc(), 10*time.Second)
	retentionWorker := retention.NewWorker(pool, cfg.RetentionInterval)
	go retentionWorker.Start(context.Background())

	// Router
	r := chi.NewRouter()

	r.Use(httpmw.PanicRecovery)
	r.Use(httpmw.RequestID)
	r.Use(httpmw.SecureHeaders)
	r.Use(httpmw.RequestTimeout(30 * time.Second))
	r.Use(httpmw.MaxBodySize(1 << 20))
	r.Use(middleware.RealIP)
	r.Use(httpmw.MetricsMiddleware)
	r.Use(httpmw.Logger)
	r.Use(httpmw.CORS)

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"ok"}`))
	})
	r.Get("/metrics", promhttp.Handler().ServeHTTP)

	r.Route("/public/v1", func(r chi.Router) {
		r.Use(httpmw.AuthenticateAPIKey(pool))
		r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/scores/{brandID}", publicHandler.GetScore)
		r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/trends", publicHandler.ListTrends)
	})

	r.Route("/v1", func(r chi.Router) {
		r.Use(httpmw.ValidateContentType("application/json"))

		r.Post("/auth/register", authHandler.Register)
		r.Post("/auth/login", authHandler.Login)
		r.Post("/auth/accept-invitation", authHandler.AcceptInvitation)
		r.Post("/sso/acs/{tenantId}", ssoHandler.HandleACS)

		r.Group(func(r chi.Router) {
			r.Use(httpmw.Authenticate(jwtService.TokenValidator(redisClient)))
			r.Use(httpmw.TenantContext(pool))
			r.Use(httpmw.RateLimit(quotaChecker, "api_requests_per_hour"))

			r.Post("/auth/logout", authHandler.Logout)
			r.Get("/tenant", authHandler.GetTenant)
			r.Get("/tenant/members", authHandler.ListMembers)
			r.Patch("/tenant/members/{userId}/role", authHandler.UpdateMemberRole)
			r.Get("/tenant/invitations", authHandler.ListInvitations)
			r.With(httpmw.RequireTier(pool, httpmw.TierPro)).Post("/tenant/invitations", authHandler.InviteMember)

			r.Post("/account/deletion", privacyHandler.RequestDeletion)
			r.Post("/privacy/delete", privacyHandler.RequestDeletion)
			r.Get("/deletion-requests", privacyHandler.ListDeletionRequests)
			r.Post("/deletion-requests/{id}/process", privacyHandler.ProcessDeletionRequest)
			r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/tenant/panorama", configHandler.ListWorkspacePanorama)

			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/api-keys", apiKeyHandler.List)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Post("/api-keys", apiKeyHandler.Create)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Delete("/api-keys/{keyId}", apiKeyHandler.Delete)

			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/admin/audit-trail", auditHandler.ListAuditTrail)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/admin/audit-trail/export", auditHandler.ExportAuditTrail)

			r.Post("/billing/checkout", billingHandler.CreateCheckoutSession)
			r.Post("/billing/webhook", billingHandler.HandleWebhook)
			r.Get("/billing/subscription", billingHandler.GetSubscription)

			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/compliance/soc2", complianceHandler.SOC2Readiness)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/compliance/report", complianceHandler.ComplianceReport)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/compliance/evidence", complianceHandler.ListEvidence)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/compliance/evidence/download", complianceHandler.DownloadEvidence)

			r.Get("/pilot/status", pilotHandler.GetStatus)
			r.Post("/pilot/enroll", pilotHandler.Enroll)
			r.Post("/pilot/extend", pilotHandler.ExtendTrial)
			r.Post("/pilot/cancel", pilotHandler.Cancel)
			r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Get("/pilot/tenants", pilotHandler.ListAll)

			r.Route("/sso", func(r chi.Router) {
				r.Use(httpmw.RequireRole(httpmw.RoleAdmin))
				r.Get("/config", ssoHandler.GetConfig)
				r.Put("/config", ssoHandler.UpdateConfig)
				r.Get("/metadata", ssoHandler.GetSPMetadata)
				r.Post("/enable", ssoHandler.Enable)
				r.Post("/disable", ssoHandler.Disable)
				r.Post("/generate-keys", ssoHandler.GenerateKeyPair)
			})

			// R1: AI Registry
			r.Route("/registry", func(r chi.Router) {
				r.Use(httpmw.RequireRole(httpmw.RoleViewer))
				r.Get("/entities", registryHandler.List)
				r.Get("/entities/{entityId}", registryHandler.Get)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/entities", registryHandler.Create)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Put("/entities/{entityId}", registryHandler.Update)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Delete("/entities/{entityId}", registryHandler.Delete)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/entities/{entityId}/assess", registryHandler.AssessRisk)
			})

			// R4: Policy Packs
			r.Route("/policies", func(r chi.Router) {
				r.Use(httpmw.RequireRole(httpmw.RoleViewer))
				r.Get("/packs", policyHandler.ListPacks)
				r.Get("/packs/{packId}/controls", policyHandler.ListControls)
				r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Post("/packs/seed", policyHandler.SeedPacks)
				r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Post("/packs/{packId}/apply", policyHandler.ApplyPack)
				r.With(httpmw.RequireRole(httpmw.RoleAdmin)).Put("/controls/{controlId}", policyHandler.UpdateControl)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/compliance/{entityId}", policyHandler.GetCompliance)
			})

			// R3: Runtime Guardrails
			r.Route("/guardrails", func(r chi.Router) {
				r.Use(httpmw.RequireRole(httpmw.RoleViewer))
				r.Get("/rules", guardrailHandler.ListRules)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/rules", guardrailHandler.CreateRule)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Put("/rules/{ruleId}/toggle", guardrailHandler.ToggleRule)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Delete("/rules/{ruleId}", guardrailHandler.DeleteRule)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/seed-defaults", guardrailHandler.SeedDefaults)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/evaluate", guardrailHandler.Evaluate)
			})

			// R2: Shadow AI Discovery
			r.Route("/discovery", func(r chi.Router) {
				r.Use(httpmw.RequireRole(httpmw.RoleAdmin))
				r.Post("/scan", discoveryHandler.StartScan)
				r.Get("/scans/{scanId}", discoveryHandler.GetScanResults)
			})

			// R5: Bias/Fairness
			r.Route("/bias", func(r chi.Router) {
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/evaluate", biasHandler.Evaluate)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/tests", biasHandler.ListTests)
			})

			// R6: CI/CD Gate
			r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/gate/check", gateHandler.Check)
			r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/gate/history/{entityId}", gateHandler.History)

			// R7: Explainability
			r.Route("/explain", func(r chi.Router) {
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Post("/{entityId}", explainHandler.Explain)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/results", explainHandler.ListAnalyses)
			})

			// R8: Agent Tracing
			r.Route("/agents", func(r chi.Router) {
				r.Use(httpmw.RequireRole(httpmw.RoleViewer))
				r.Post("/traces", agentHandler.StartTrace)
				r.Get("/traces/{traceId}", agentHandler.GetTrace)
				r.Get("/traces", agentHandler.ListTraces)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/traces/{traceId}/steps", agentHandler.RecordStep)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/traces/{traceId}/complete", agentHandler.CompleteTrace)
			})

			// R9: Prompt Audit
			r.Route("/prompts", func(r chi.Router) {
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/audit", promptHandler.RunAudit)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/audits", promptHandler.ListAudits)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/audits/{auditId}", promptHandler.GetAudit)
			})

			// R10: Model Benchmark
			r.Route("/benchmarks", func(r chi.Router) {
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/models", benchmarkHandler.RunBenchmark)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/models", benchmarkHandler.ListBenchmarks)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/compare", benchmarkHandler.CompareModels)
			})

			// R11: Cost Analytics
			r.Route("/costs", func(r chi.Router) {
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/entries", costHandler.RecordCost)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/entries", costHandler.ListCosts)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/summary", costHandler.GetCostSummary)
			})

			// R12: Usage Analytics
			r.Route("/usage", func(r chi.Router) {
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/metrics", usageHandler.RecordUsage)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/metrics", usageHandler.ListUsage)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/summary", usageHandler.GetUsageSummary)
			})

			// R13: Optimization Recommendations
			r.Route("/optimizations", func(r chi.Router) {
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/recommendations", optimizeHandler.ListRecommendations)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/recommendations/generate", optimizeHandler.GenerateRecommendations)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Put("/recommendations/{recId}/status", optimizeHandler.UpdateStatus)
			})

			// R14: Version Tracking
			r.Route("/versions", func(r chi.Router) {
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/entries", versionHandler.RecordVersion)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/entries", versionHandler.ListVersions)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/entries/{entryId}", versionHandler.GetVersionDiff)
			})

			// R15: Incident Management
			r.Route("/incidents", func(r chi.Router) {
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/events", incidentHandler.ListIncidents)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Post("/events", incidentHandler.CreateIncident)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Put("/events/{incidentId}", incidentHandler.UpdateIncident)
			})

			// Workspace-scoped routes
			r.Route("/workspaces/{ws}", func(r chi.Router) {
				r.Use(httpmw.RequireWorkspace)
				r.Use(httpmw.RequireWorkspaceAccess(pool))

				var cacheCfg httpmw.CacheConfig
				if redisClient != nil {
					cacheCfg = httpmw.CacheConfig{RDB: redisClient, TTL: 30 * time.Second}
				}

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

				r.Group(func(r chi.Router) {
					r.Use(httpmw.RequireRole(httpmw.RoleAdmin))
					r.Post("/brands", configHandler.CreateBrand)
					r.Post("/archive", configHandler.ArchiveWorkspace)
					r.Post("/unarchive", configHandler.UnarchiveWorkspace)
					r.Post("/transfer", configHandler.TransferWorkspace)
				})

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
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/measurements/{runId}/status", measureHandler.GetMeasurementStatus)

				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/retention/policies", retentionHandler.ListPolicies)
				r.With(httpmw.RequireRole(httpmw.RoleViewer)).Get("/retention/archive-summary", retentionHandler.GetArchiveSummary)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Put("/retention/policies", retentionHandler.UpsertPolicy)
				r.With(httpmw.RequireRole(httpmw.RoleEditor)).Delete("/retention/policies/{policyId}", retentionHandler.DeletePolicy)

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
					r.Post("/reports", pdfHandler.RequestReport)

					r.Group(func(r chi.Router) {
						r.Use(httpmw.RequireTier(pool, httpmw.TierPro))
						r.Post("/reports/digest", pdfHandler.GenerateWeeklyDigest)
						r.Post("/reports/score-card", pdfHandler.GenerateScoreCard)
						r.Post("/reports/audit", pdfHandler.GenerateAuditReport)
					})

					r.Post("/alert-rules", alertHandler.Create)
					r.Put("/alert-rules/{ruleId}", alertHandler.Update)
					r.Delete("/alert-rules/{ruleId}", alertHandler.Delete)
				})
			})
		})
	})

	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      r,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		slog.Info("api sunucusu başlatılıyor", "port", cfg.Port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("sunucu hatası", "error", err)
			return
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
		return
	}
}
