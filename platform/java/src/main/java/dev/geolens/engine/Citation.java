package dev.geolens.engine;

/** Bir AI yanıtından çıkarılan alıntı — Go {@code engine.Citation} portu. */
public record Citation(
        String url,
        String title,
        int position,
        String engine,
        String domain,
        String type) {

    public Citation {
        if (url == null) {
            url = "";
        }
        if (title == null) {
            title = "";
        }
        if (domain == null) {
            domain = "";
        }
        if (type == null) {
            type = "direct";
        }
    }

    /** Domain'i boş bırakan doğrudan alıntı fabrikası (Go'daki {@code engine.Citation} kullanımı karşılığı). */
    public static Citation direct(String url, String title, int position, String engine) {
        return new Citation(url, title, position, engine, "", "direct");
    }
}