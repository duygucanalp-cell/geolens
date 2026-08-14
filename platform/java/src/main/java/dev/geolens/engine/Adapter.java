package dev.geolens.engine;

/**
 * Tüm AI motoru adaptörlerinin uygulaması gereken arayüz — Go {@code engine.Adapter} portu.
 * Her adaptör {@link #execute} çağrısı içinde API isteğini yapar ve yanıtı ayrıştırır.
 * Ham JSON ayrıştırma adaptör içinde private olarak kalır, dışa açılmaz.
 */
public interface Adapter {

    /** Motorun benzersiz adı (örn. "chatgpt", "gemini", "perplexity"). */
    String name();

    /** Motorun erişim kademesi. */
    Tier tier();

    /** Prompt'u AI motoruna gönderir ve normalleştirilmiş yanıtı döner. */
    RawResponse execute(String prompt) throws EngineException;

    /**
     * Tenant/workspace bağlamıyla bir kopya üretir.
     * Orijinal adaptör mutasyona uğramaz — eşzamanlı isteklerde race condition önlenir.
     */
    Adapter withContext(String tenantId, String workspaceId);
}