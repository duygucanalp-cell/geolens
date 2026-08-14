package dev.geolens.engine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Kayıtlı motor adaptörlerini yönetir — Go {@code engine.Registry} portu. */
public final class Registry {

    private final Map<String, Adapter> adapters = new ConcurrentHashMap<>();

    /** Bir adaptörü ismine göre kayıt eder. */
    public void register(Adapter adapter) {
        adapters.put(adapter.name(), adapter);
    }

    /** Ada göre adaptör döner; bulunamazsa {@code null}. */
    public Adapter get(String name) {
        return adapters.get(name);
    }

    /** Kayıtlı tüm motor adlarını döner. */
    public List<String> list() {
        return List.copyOf(adapters.keySet());
    }

    /** Kayıtlı adaptör sayısı. */
    public int count() {
        return adapters.size();
    }
}