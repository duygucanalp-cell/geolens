package dev.geolens.billing;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * GİB e-Fatura/e-Arşiv UBL-TR formatının gerekli alt kümesini üretir — Go {@code BuildUBLTREInvoice} portu.
 * <p>Tüm para tutarları kuruş cinsindendir; XML'de TL olarak 2 ondalık biçimlenir.
 */
public final class UblTrInvoice {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private UblTrInvoice() {
    }

    /** GİB UBL InvoiceTypeCode: e-Fatura → SATIS (satış faturası), e-Arşiv → EARSIV. */
    public static String invoiceTypeCodeFor(InvoiceType type) {
        return type == InvoiceType.EARSIV ? "EARSIV" : "SATIS";
    }

    /** Faturanın UBL-TR XML belgesini üretir. */
    public static String build(InvoiceDocument doc) {
        if (doc == null) {
            throw new IllegalArgumentException("fatura dokümanı boş");
        }
        if (doc.documentId() == null || doc.documentId().isBlank()
                || doc.number() == null || doc.number().isBlank()) {
            throw new IllegalArgumentException("belge kimliği ve numara zorunludur");
        }

        String currency = doc.currency() == null || doc.currency().isBlank() ? "TRY" : doc.currency();

        StringBuilder customer = new StringBuilder();
        if (doc.customerTaxNo() != null && !doc.customerTaxNo().isBlank()) {
            customer.append("""
                    <cac:PartyTaxScheme>
                      <cbc:Identifier>""").append(escape(doc.customerTaxNo())).append("""
                    </cbc:Identifier>
                      <cbc:Name></cbc:Name>
                      <cac:TaxScheme>
                        <cbc:ID>VKN</cbc:ID>
                        <cbc:Name>Vergi Kimlik Numarası</cbc:Name>
                      </cac:TaxScheme>
                    </cac:PartyTaxScheme>
                    """);
        } else if (doc.customerIdentity() != null && !doc.customerIdentity().isBlank()) {
            customer.append("""
                    <cac:Person>
                      <cbc:FirstName>""").append(escape(name(doc))).append("""
                    </cbc:FirstName>
                      <cbc:FamilyName></cbc:FamilyName>
                    </cac:Person>
                    <cac:PartyTaxScheme>
                      <cbc:Identifier>""").append(escape(doc.customerIdentity())).append("""
                    </cbc:Identifier>
                      <cbc:Name></cbc:Name>
                      <cac:TaxScheme>
                        <cbc:ID>TCKN</cbc:ID>
                        <cbc:Name>T.C. Kimlik Numarası</cbc:Name>
                      </cac:TaxScheme>
                    </cac:PartyTaxScheme>
                    """);
        }
        if (doc.customerAddress() != null && !doc.customerAddress().isBlank()) {
            customer.append("""
                    <cac:PostalAddress>
                      <cbc:StreetName>""").append(escape(doc.customerAddress())).append("""
                    </cbc:StreetName>
                    </cac:PostalAddress>
                    """);
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2" xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  <cbc:UBLVersionID>2.1</cbc:UBLVersionID>
                  <cbc:CustomizationID>TR1.2</cbc:CustomizationID>
                  <cbc:ProfileID>TICARIFATURA</cbc:ProfileID>
                  <cbc:ID>%s</cbc:ID>
                  <cbc:CopyIndicator>false</cbc:CopyIndicator>
                  <cbc:UUID>%s</cbc:UUID>
                  <cbc:IssueDate>%s</cbc:IssueDate>
                  <cbc:IssueTime>%s</cbc:IssueTime>
                  <cbc:InvoiceTypeCode>%s</cbc:InvoiceTypeCode>
                  <cbc:Note>Bu belge GeoLens AI Visibility Platform tarafından otomatik oluşturulmuştur.</cbc:Note>
                  <cbc:DocumentCurrencyCode>%s</cbc:DocumentCurrencyCode>
                  <cac:AccountingSupplierParty>
                    <cac:Party>
                      <cac:PartyName>
                        <cbc:Name>GeoLens Teknoloji A.Ş.</cbc:Name>
                      </cac:PartyName>
                    </cac:Party>
                  </cac:AccountingSupplierParty>
                  <cac:AccountingCustomerParty>
                    <cac:Party>
                      <cac:PartyName>
                        <cbc:Name>%s</cbc:Name>
                      </cac:PartyName>
                %s
                    </cac:Party>
                  </cac:AccountingCustomerParty>
                  <cac:TaxTotal>
                    <cbc:TaxAmount>%s</cbc:TaxAmount>
                    <cac:TaxSubtotal>
                      <cbc:TaxableAmount>%s</cbc:TaxableAmount>
                      <cbc:TaxAmount>%s</cbc:TaxAmount>
                      <cac:TaxCategory>
                        <cbc:ID>S</cbc:ID>
                        <cbc:Percent>%d</cbc:Percent>
                        <cac:TaxScheme>
                          <cbc:ID>KDV</cbc:ID>
                          <cbc:Name>Katma Değer Vergisi</cbc:Name>
                        </cac:TaxScheme>
                      </cac:TaxCategory>
                    </cac:TaxSubtotal>
                  </cac:TaxTotal>
                  <cac:LegalMonetaryTotal>
                    <cbc:LineExtensionAmount>%s</cbc:LineExtensionAmount>
                    <cbc:TaxExclusiveAmount>%s</cbc:TaxExclusiveAmount>
                    <cbc:TaxInclusiveAmount>%s</cbc:TaxInclusiveAmount>
                    <cbc:PayableAmount>%s</cbc:PayableAmount>
                  </cac:LegalMonetaryTotal>
                </Invoice>
                """.formatted(
                escape(doc.number()),
                escape(doc.documentId()),
                doc.issueDate().format(DATE),
                doc.issueDate().format(TIME),
                invoiceTypeCodeFor(doc.invoiceType()),
                escape(currency),
                escape(name(doc)),
                customer,
                amount(doc.vatAmount()),
                amount(doc.subtotal()),
                amount(doc.vatAmount()),
                doc.vatRate(),
                amount(doc.subtotal()),
                amount(doc.subtotal()),
                amount(doc.total()),
                amount(doc.total()));
    }

    private static String name(InvoiceDocument doc) {
        return doc.customerName() == null ? "" : doc.customerName();
    }

    /** Kuruş → TL (2 ondalık) — Go {@code fmt.Sprintf("%.2f", v/100)} karşılığı. */
    private static String amount(long kurus) {
        return String.format(Locale.ROOT, "%.2f", kurus / 100.0);
    }

    /** XML metin düğümü kaçışı. */
    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
