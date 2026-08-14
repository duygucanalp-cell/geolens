package dev.geolens.archive;

import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * Response archive motoru — Go {@code archive/engine.go} portu (FR-D13).
 * <p>Yanıtı SHA-256 hash + 1000 karakter önizleme ile arşivler; marka+engine
 * bazında sıradaki versiyonu (MAX+1) belirler.
 */
public class ArchiveEngine {

    private final DSLContext dsl;

    public ArchiveEngine(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Go {@code Archive} karşılığı: yanıtı arşive ekler, marka+engine bazında
     * sıradaki versiyonu hesaplar (versiyon sorgusu hatasında 0'dan devam).
     */
    public Entry archive(String brandId, String engineName, String promptText, String response,
                         String workspaceId, String tenantId) {
        String contentHash = sha256Hex(response);
        String preview = response.length() > 1000 ? response.substring(0, 1000) : response;

        // Sıradaki versiyon — MAX(version)+1 (sorgu hatasında 0 → next 1)
        int currentVersion = 0;
        try {
            Record r = dsl.fetchOne("""
                    SELECT COALESCE(MAX(version), 0) FROM archive.response_entries
                    WHERE brand_id = ? AND engine_name = ? AND tenant_id = ?
                    """, brandId, engineName, tenantId);
            if (r != null) {
                currentVersion = ((Number) r.get(0)).intValue();
            }
        } catch (RuntimeException e) {
            // Go'da warn loglanıp 0 kalır
        }
        int nextVersion = currentVersion + 1;

        Entry entry = new Entry(
                Ulid.generate(), brandId, engineName, promptText, preview, response,
                null, nextVersion, contentHash, tenantId);

        dsl.execute("""
                INSERT INTO archive.response_entries
                    (id, brand_id, engine_name, prompt_text, response_preview, response_full,
                     version, content_hash, tenant_id, workspace_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """, entry.id(), entry.brandId(), entry.engineName(), entry.promptText(),
                entry.responsePreview(), entry.responseFull(), entry.version(),
                entry.contentHash(), entry.tenantId(), workspaceId);

        return entry;
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
