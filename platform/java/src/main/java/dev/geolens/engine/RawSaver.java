package dev.geolens.engine;

/** Ham API yanıtlarını kalıcı depoya kaydeden arayüz — Go {@code engine.RawSaver} portu. */
public interface RawSaver {

    /** Ham yanıtı kaydeder ve depolama anahtarını döner. Hata durumunda {@link EngineException} fırlatır. */
    String saveRawResponse(String tenantId, String workspaceId, String engineName, byte[] data);
}