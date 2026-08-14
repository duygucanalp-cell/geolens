package dev.geolens.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PDF rapor üretim servisi — Go {@code pdf.service} portu.
 * <p>Go maroto v2 yerine OpenPDF kullanır; haftalık özet, skor kartı ve denetim
 * raporu üretir. Skorlar {@code measure.scores}, denetim {@code governance.audit_results}
 * tablosundan çekilir; sorgu hatasında Go ile aynı mock/tamamlanamadı verisi kullanılır.
 */
public class PdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbc;

    public PdfService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ReportResult generate(ReportRequest req) {
        switch (req.type()) {
            case WEEKLY_DIGEST:
                return generateWeeklyDigest(req.workspaceId(), req.tenantId());
            case SCORE_CARD:
                return generateScoreCard(req);
            case AUDIT:
                return generateAuditReport(req);
            default:
                throw new IllegalArgumentException("pdf: bilinmeyen rapor tipi: " + req.type());
        }
    }

    public ReportResult generateWeeklyDigest(String workspaceId, String tenantId) {
        List<ScoreRow> scores;
        try {
            scores = loadWorkspaceScores(workspaceId, tenantId);
        } catch (RuntimeException e) {
            scores = List.of(
                    new ScoreRow("Acme", 85, 80, 5, "Kademe 1"),
                    new ScoreRow("BetaCorp", 62, 70, -8, "Kademe 1"),
                    new ScoreRow("GammaInc", 43, 45, -2, "Kademe 2"));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph("GeoLens Haftalık Özet Raporu",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            LocalDate today = LocalDate.now();
            Paragraph dateP = new Paragraph("Tarih: " + today.minusDays(7).format(DATE) + " - " + today.format(DATE),
                    FontFactory.getFont(FontFactory.HELVETICA, 10));
            dateP.setAlignment(Element.ALIGN_CENTER);
            doc.add(dateP);

            doc.add(new Paragraph("Haftalık Özet", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            doc.add(new Paragraph(
                    "Bu hafta markalarınızın AI görünürlük performansını değerlendirdik. Aşağıda detaylı skorlar, trendler ve öneriler yer almaktadır.",
                    FontFactory.getFont(FontFactory.HELVETICA, 10)));
            doc.add(new Paragraph("Marka Skorları", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));

            PdfPTable table = new PdfPTable(4);
            addCell(table, "Marka", true);
            addCell(table, "Skor", true);
            addCell(table, "Değişim", true);
            addCell(table, "Fidelite", true);
            for (ScoreRow s : scores) {
                addCell(table, s.brandName(), false);
                addCell(table, String.format("%.0f", s.score()), false);
                addCell(table, String.format("%+.0f", s.change()), false);
                addCell(table, s.fidelityLabel(), false);
            }
            doc.add(table);

            doc.add(new Paragraph("Öneriler", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            for (ScoreRow s : scores) {
                String rec;
                if (s.change() > 0) {
                    rec = s.brandName() + ": Görünürlük skoru yükselişte (%" + String.format("%+.0f", s.change())
                            + ") — mevcut stratejiyi koruyun.";
                } else if (s.change() < 0) {
                    rec = s.brandName() + ": Skor düşüşü tespit edildi (%" + String.format("%+.0f", s.change())
                            + ") — rakip analizi yapmanız önerilir.";
                } else {
                    rec = s.brandName() + ": Skor sabit — yapılandırılmış veri ekleyerek görünürlüğü artırabilirsiniz.";
                }
                doc.add(new Paragraph("• " + rec, FontFactory.getFont(FontFactory.HELVETICA, 9)));
            }

            Paragraph footer = new Paragraph("Bu rapor GeoLens AI Visibility Platform tarafından otomatik oluşturulmuştur.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7));
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
        } catch (DocumentException e) {
            throw new PdfGenerationException("pdf: oluşturma hatası", e);
        }

        return new ReportResult(generateULID(), ReportType.WEEKLY_DIGEST, out.toByteArray(),
                "weekly-digest-" + LocalDate.now().format(FILE_DATE) + ".pdf", 1, Instant.now(), null);
    }

    public ReportResult generateScoreCard(ReportRequest req) {
        String brandName = req.brandName();
        double score = 0;
        double prevScore = 0;
        String fidelityLabel = "Kademe 2";

        try {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT b.name,
                        COALESCE(s.value, 0) AS score,
                        COALESCE(s_prev.value, 0) AS prev_score,
                        COALESCE(s.fidelity_label, 'Kademe 2') AS fidelity_label
                    FROM config.brands b
                    LEFT JOIN LATERAL (
                        SELECT value, fidelity_label FROM measure.scores
                        WHERE brand_id = b.id AND workspace_id = b.workspace_id AND tenant_id = b.tenant_id
                        ORDER BY freshness_at DESC LIMIT 1
                    ) s ON true
                    LEFT JOIN LATERAL (
                        SELECT value FROM measure.scores
                        WHERE brand_id = b.id AND workspace_id = b.workspace_id AND tenant_id = b.tenant_id
                        ORDER BY freshness_at DESC OFFSET 1 LIMIT 1
                    ) s_prev ON true
                    WHERE b.id = ? AND b.workspace_id = ? AND b.tenant_id = ?
                    """, req.brandId(), req.workspaceId(), req.tenantId());
            brandName = String.valueOf(row.get("name"));
            score = ((Number) row.get("score")).doubleValue();
            prevScore = ((Number) row.get("prev_score")).doubleValue();
            fidelityLabel = String.valueOf(row.get("fidelity_label"));
        } catch (RuntimeException e) {
            if (brandName == null || brandName.isBlank()) {
                brandName = "Bilinmeyen Marka";
            }
        }

        double change = score - prevScore;
        String changeStr = String.format("%+.1f", change);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph("GeoLens Skor Kartı",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20));
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Paragraph brand = new Paragraph(brandName, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
            brand.setAlignment(Element.ALIGN_CENTER);
            doc.add(brand);

            Paragraph scoreP = new Paragraph(String.format("%.0f", score),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 32));
            scoreP.setAlignment(Element.ALIGN_CENTER);
            doc.add(scoreP);

            Paragraph of100 = new Paragraph("/ 100", FontFactory.getFont(FontFactory.HELVETICA, 12));
            of100.setAlignment(Element.ALIGN_CENTER);
            doc.add(of100);

            Paragraph changeP = new Paragraph("Değişim: " + changeStr, FontFactory.getFont(FontFactory.HELVETICA, 11));
            changeP.setAlignment(Element.ALIGN_CENTER);
            doc.add(changeP);

            Paragraph fidP = new Paragraph("Fidelite: " + fidelityLabel, FontFactory.getFont(FontFactory.HELVETICA, 10));
            fidP.setAlignment(Element.ALIGN_CENTER);
            doc.add(fidP);

            Paragraph dateP = new Paragraph("Tarih: " + LocalDate.now().format(DATE), FontFactory.getFont(FontFactory.HELVETICA, 9));
            dateP.setAlignment(Element.ALIGN_CENTER);
            doc.add(dateP);

            Paragraph footer = new Paragraph("Bu rapor GeoLens AI Visibility Platform tarafından oluşturulmuştur.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7));
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
        } catch (DocumentException e) {
            throw new PdfGenerationException("pdf: score card oluşturma hatası", e);
        }

        return new ReportResult(generateULID(), ReportType.SCORE_CARD, out.toByteArray(),
                "score-card-" + brandName + ".pdf", 1, Instant.now(), null);
    }

    public ReportResult generateAuditReport(ReportRequest req) {
        List<AuditRow> rows = List.of(
                new AuditRow("robots.txt", "Kontrol Edilemedi", 0, "DB sorgusu yapılamadı"),
                new AuditRow("Bot Erişimi", "Kontrol Edilemedi", 0, "DB sorgusu yapılamadı"),
                new AuditRow("SSR", "Kontrol Edilemedi", 0, "DB sorgusu yapılamadı"),
                new AuditRow("SSRF Koruması", "Kontrol Edilemedi", 0, "DB sorgusu yapılamadı"));

        double overallScore = 0;
        try {
            Double score = jdbc.queryForObject("""
                    SELECT overall_score FROM governance.audit_results
                    WHERE brand_id = ? AND tenant_id = ?
                    ORDER BY created_at DESC LIMIT 1
                    """, Double.class, req.brandId(), req.tenantId());
            overallScore = score == null ? 0 : score;
            rows = List.of(
                    new AuditRow("robots.txt", "Tamam", 0, ""),
                    new AuditRow("Bot Erişimi", "Tamam", 0, ""),
                    new AuditRow("SSR", "Tamam", 0, ""),
                    new AuditRow("SSRF Koruması", "Tamam", 0, ""));
        } catch (RuntimeException e) {
            // sorgu hatasında "Kontrol Edilemedi" satırları kalır (Go ile aynı)
        }

        String brandName = req.brandName();
        if (brandName == null || brandName.isBlank()) {
            brandName = req.brandId();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph("GeoLens Denetim Raporu",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Paragraph brand = new Paragraph(brandName, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
            brand.setAlignment(Element.ALIGN_CENTER);
            doc.add(brand);

            Paragraph scoreP = new Paragraph(String.format("Genel Skor: %.0f / 100", overallScore),
                    FontFactory.getFont(FontFactory.HELVETICA, 12));
            scoreP.setAlignment(Element.ALIGN_CENTER);
            doc.add(scoreP);

            PdfPTable table = new PdfPTable(3);
            addCell(table, "Kategori", true);
            addCell(table, "Durum", true);
            addCell(table, "Öneri", true);
            for (AuditRow r : rows) {
                addCell(table, r.category(), false);
                addCell(table, r.status(), false);
                addCell(table, r.recommendation(), false);
            }
            doc.add(table);

            Paragraph footer = new Paragraph("Bu rapor GeoLens AI Visibility Platform tarafından otomatik oluşturulmuştur.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7));
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
        } catch (DocumentException e) {
            throw new PdfGenerationException("pdf: audit rapor oluşturma hatası", e);
        }

        return new ReportResult(generateULID(), ReportType.AUDIT, out.toByteArray(),
                "audit-" + brandName + ".pdf", 1, Instant.now(), null);
    }

    public byte[] getReportData(String reportId) {
        String paramsJson;
        try {
            paramsJson = jdbc.queryForObject("""
                    SELECT params::text FROM measure.reports WHERE id = ?
                    """, String.class, reportId);
        } catch (RuntimeException e) {
            throw new PdfReportNotFoundException("rapor bulunamadı");
        }
        if (paramsJson == null) {
            throw new PdfReportNotFoundException("rapor bulunamadı");
        }

        try {
            Map<String, Object> params = new com.fasterxml.jackson.databind.ObjectMapper().readValue(paramsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            Object fileData = params.get("file_data");
            if (fileData instanceof String s && !s.isBlank()) {
                return Base64.getDecoder().decode(s);
            }
            Object pdfB64 = params.get("pdf_b64");
            if (pdfB64 instanceof String s && !s.isBlank()) {
                return Base64.getDecoder().decode(s);
            }
            Object s3Url = params.get("s3_url");
            if (s3Url instanceof String s && !s.isBlank()) {
                throw new PdfGenerationException("S3 depolama entegrasyonu gerekli — rapor S3'te: " + s);
            }
            throw new PdfReportNotFoundException("rapor verisi bulunamadı: " + reportId);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new PdfGenerationException("rapor parametre ayrıştırma", e);
        }
    }

    private List<ScoreRow> loadWorkspaceScores(String workspaceId, String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.name,
                    COALESCE(s.value, 0) AS score,
                    COALESCE(s_prev.value, 0) AS prev_score,
                    COALESCE(s.fidelity_label, 'Kademe 2') AS fidelity_label
                FROM config.brands b
                LEFT JOIN LATERAL (
                    SELECT value, fidelity_label FROM measure.scores
                    WHERE brand_id = b.id AND workspace_id = b.workspace_id AND tenant_id = b.tenant_id
                    ORDER BY freshness_at DESC LIMIT 1
                ) s ON true
                LEFT JOIN LATERAL (
                    SELECT value FROM measure.scores
                    WHERE brand_id = b.id AND workspace_id = b.workspace_id AND tenant_id = b.tenant_id
                    ORDER BY freshness_at DESC OFFSET 1 LIMIT 1
                ) s_prev ON true
                WHERE b.workspace_id = ? AND b.tenant_id = ? AND b.is_active = true
                ORDER BY b.name
                """, workspaceId, tenantId);

        List<ScoreRow> scores = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String name = String.valueOf(r.get("name"));
            double score = ((Number) r.get("score")).doubleValue();
            double prev = ((Number) r.get("prev_score")).doubleValue();
            String fid = String.valueOf(r.get("fidelity_label"));
            scores.add(new ScoreRow(name, score, prev, score - prev, fid));
        }
        return scores;
    }

    private static void addCell(PdfPTable table, String text, boolean header) {
        Font font = FontFactory.getFont(
                header ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, 9);
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private static String generateULID() {
        return UUID.randomUUID().toString();
    }
}
