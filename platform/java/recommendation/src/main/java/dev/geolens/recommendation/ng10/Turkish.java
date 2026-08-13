package dev.geolens.recommendation.ng10;

/** Türkçe küçük harfe çevirme — Go {@code toLowerTurkish} portu (birebir). */
public final class Turkish {

    private Turkish() {
    }

    public static String toLowerCase(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            if (cp == 'İ') {
                sb.append('i');
            } else if (cp == 'I') {
                sb.append('ı');
            } else if (cp == 'Ş') {
                sb.append('ş');
            } else if (cp == 'Ç') {
                sb.append('ç');
            } else if (cp == 'Ü') {
                sb.append('ü');
            } else if (cp == 'Ö') {
                sb.append('ö');
            } else if (cp == 'Ğ') {
                sb.append('ğ');
            } else if (cp >= 'A' && cp <= 'Z') {
                sb.append((char) (cp + 32));
            } else {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }
}