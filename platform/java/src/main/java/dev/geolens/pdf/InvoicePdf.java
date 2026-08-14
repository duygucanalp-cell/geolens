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

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * FR-A6 TR özel kapsamındaki Türkçe fatura PDF şablonu — Go {@code pdf.RenderInvoice} portu.
 * <p>Go maroto v2 yerine OpenPDF kullanır; KDV kırılımı, e-Fatura/e-Arşiv bilgileri ve
 * GİB durumunu gösterir. Tutarlar kuruş cinsindendir, PDF'te TL olarak biçimlenir.
 */
public final class InvoicePdf {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private InvoicePdf() {
    }

    public static byte[] render(InvoiceData inv) {
        String currency = inv.currency() == null || inv.currency().isBlank() ? "TRY" : inv.currency();
        String amountTotal = amount(currency, inv.total());
        String amountSubtotal = amount(currency, inv.subtotal());
        String amountVat = amount(currency, inv.vatAmount());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // Başlık
            Paragraph title = new Paragraph("FATURA",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20));
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            Paragraph subtitle = new Paragraph("GeoLens AI Visibility Platform",
                    FontFactory.getFont(FontFactory.HELVETICA, 11));
            subtitle.setAlignment(Element.ALIGN_CENTER);
            doc.add(subtitle);
            doc.add(new Paragraph(" "));

            // Fatura bilgileri
            Font meta = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font boldMeta = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            PdfPTable head = new PdfPTable(2);
            head.setWidthPercentage(100);
            cell(head, "Fatura No: " + nz(inv.number()), boldMeta);
            cell(head, "Tarih: " + (inv.createdAt() == null ? "" : inv.createdAt().format(DATE)), meta);
            doc.add(head);

            if (inv.documentId() != null && !inv.documentId().isBlank()) {
                doc.add(new Paragraph("Belge Kimliği (UUID): " + inv.documentId(),
                        FontFactory.getFont(FontFactory.HELVETICA, 9)));
            }
            doc.add(new Paragraph(" "));

            // Satıcı
            doc.add(new Paragraph("SATICI", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
            doc.add(new Paragraph("GeoLens Teknoloji A.Ş.", meta));

            // Alıcı
            doc.add(new Paragraph("ALICI", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
            doc.add(new Paragraph(nz(inv.customerName()), meta));
            if (inv.customerTaxNo() != null && !inv.customerTaxNo().isBlank()) {
                doc.add(new Paragraph("Vergi Kimlik No: " + inv.customerTaxNo(), meta));
            } else if (inv.customerIdentity() != null && !inv.customerIdentity().isBlank()) {
                doc.add(new Paragraph("T.C. Kimlik No: " + inv.customerIdentity(), meta));
            }
            if (inv.customerAddress() != null && !inv.customerAddress().isBlank()) {
                doc.add(new Paragraph(inv.customerAddress(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
            }
            doc.add(new Paragraph(" "));

            // Dönem
            if (inv.periodStart() != null || inv.periodEnd() != null) {
                String start = inv.periodStart() == null ? "-" : inv.periodStart().format(DATE);
                String end = inv.periodEnd() == null ? "-" : inv.periodEnd().format(DATE);
                doc.add(new Paragraph("Dönem: " + start + " - " + end, meta));
                doc.add(new Paragraph(" "));
            }

            // Tutar kırılımı
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            cell(table, "Açıklama", headerFont);
            cell(table, "Tutar", headerFont);

            String desc = "GeoLens abonelik ücreti";
            if ("efatura".equals(inv.invoiceType()) || "earsiv".equals(inv.invoiceType())) {
                desc = "GeoLens abonelik ücreti (KDV dahil)";
            }
            Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            cell(table, desc, rowFont);
            cell(table, amountSubtotal, rowFont);
            cell(table, "KDV (" + inv.vatRate() + "%)", rowFont);
            cell(table, amountVat, rowFont);
            cell(table, "GENEL TOPLAM", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
            cell(table, amountTotal, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
            doc.add(table);
            doc.add(new Paragraph(" "));

            // e-Fatura / GİB durumu
            String invoiceTypeLabel = "Standart Fatura";
            if ("efatura".equals(inv.invoiceType())) {
                invoiceTypeLabel = "e-Fatura";
            } else if ("earsiv".equals(inv.invoiceType())) {
                invoiceTypeLabel = "e-Arşiv";
            }
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9);
            doc.add(new Paragraph("Fatura Tipi: " + invoiceTypeLabel, small));
            doc.add(new Paragraph("GİB Durumu: " + gibLabel(inv.gibStatus()), small));
            doc.add(new Paragraph(" "));

            Paragraph footer = new Paragraph(
                    "Bu fatura GeoLens AI Visibility Platform tarafından otomatik oluşturulmuştur.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7));
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
        } catch (DocumentException e) {
            throw new PdfGenerationException("pdf: fatura oluşturma hatası", e);
        }
        return out.toByteArray();
    }

    private static String gibLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "none" -> "GİB gönderimi yok";
            case "pending" -> "GİB gönderimde";
            case "accepted" -> "GİB tarafından kabul edildi";
            case "rejected" -> "GİB tarafından reddedildi";
            default -> status;
        };
    }

    private static String amount(String currency, long kurus) {
        return String.format(Locale.ROOT, "%s%.2f", currencySymbol(currency), kurus / 100.0);
    }

    private static String currencySymbol(String currency) {
        return switch (currency) {
            case "usd" -> "$";
            case "eur" -> "€";
            default -> "₺";
        };
    }

    private static void cell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setBorder(0);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
