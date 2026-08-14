package dev.geolens.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Go {@code registry_test.go} portu. */
class RegistryTest {

    private static Adapter mock(String name, Tier tier) {
        return new Adapter() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Tier tier() {
                return tier;
            }

            @Override
            public RawResponse execute(String prompt) {
                return new RawResponse(name, "", "mock", null, false, tier, "", "");
            }

            @Override
            public Adapter withContext(String tenantId, String workspaceId) {
                return this;
            }
        };
    }

    @Test
    void registerAndGet() {
        Registry r = new Registry();
        Adapter a = mock("test", Tier.DIRECT);
        r.register(a);

        Adapter got = r.get("test");
        assertEquals("test", got.name());
    }

    @Test
    void getUnknown() {
        Registry r = new Registry();
        assertNull(r.get("unknown"));
    }

    @Test
    void list() {
        Registry r = new Registry();
        r.register(mock("a", Tier.DIRECT));
        r.register(mock("b", Tier.DIRECT));
        r.register(mock("c", Tier.DIRECT));

        assertEquals(3, r.list().size());
    }

    @Test
    void count() {
        Registry r = new Registry();
        r.register(mock("x", Tier.DIRECT));
        r.register(mock("y", Tier.DIRECT));
        assertEquals(2, r.count());
    }

    @Test
    void emptyCount() {
        Registry r = new Registry();
        assertEquals(0, r.count());
    }
}