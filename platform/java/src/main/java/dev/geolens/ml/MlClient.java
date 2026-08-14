package dev.geolens.ml;

/**
 * ML serving prompt sınıflandırıcı arayüzü — Go {@code ml.Client} portu (0421 A0-3).
 * Serving ulaşılamazsa veya hata dönerse çağıran taraf varsayılan GAVF ağırlıklarına
 * fallback yapar (0421 M-4). Yapılandırılmadığında (ML_SERVING_URL boş) davranış
 * kural tabanlıdır.
 */
public interface MlClient {

    /** Prompt'u intent/topic/persona/funnel hedeflerine göre sınıflandırır. */
    PromptClassification classifyPrompt(String text) throws ServingException;
}