package dev.geolens.publicapi.service;

/** Rapor indirme sonucu — ya S3 yönlendirmesi ya da PDF verisi taşır. */
public record ReportDownload(String location, byte[] data, String fileName) {

    public static ReportDownload redirect(String location) {
        return new ReportDownload(location, null, "");
    }

    public static ReportDownload pdf(byte[] data, String fileName) {
        return new ReportDownload(null, data, fileName == null ? "" : fileName);
    }

    public boolean isRedirect() {
        return location != null && !location.isBlank();
    }
}
