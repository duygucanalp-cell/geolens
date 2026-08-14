package dev.geolens.testutil;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.Table;
import org.jooq.TableLike;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.util.Collection;
import java.util.List;

import static dev.geolens.jooq.version.tables.Entries.ENTRIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Scratch probe — dsl mock'lamada hangi desenlerin eşleştiğini kanıtlar. */
class JooqMatcherProbeTest {

    private final DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);

    @Test
    void selectCollectionChainMatches() {
        when(dsl.select(any(Collection.class)).from(any(TableLike.class)).where(any(Condition.class))
                .orderBy(any(OrderField.class)).limit(anyInt()).fetch())
                .thenReturn(JooqTestData.records(List.of(
                        java.util.Map.of("id", "V01"))));

        var rows = dsl.select(List.of(ENTRIES.ID, ENTRIES.ENTITY_TYPE, ENTRIES.ENTITY_ID))
                .from(ENTRIES)
                .where(ENTRIES.TENANT_ID.eq("T01"))
                .orderBy(ENTRIES.CREATED_AT.desc())
                .limit(5)
                .fetch()
                .intoMaps();

        assertEquals(1, rows.size());
        assertEquals("V01", rows.get(0).get("id"));
    }

    @Test
    void selectCollectionFetchOneMatches() {
        when(dsl.select(any(Collection.class)).from(any(TableLike.class)).where(any(Condition.class)).fetchOne())
                .thenReturn(JooqTestData.record(java.util.Map.of("id", "V01")));

        var row = dsl.select(List.of(ENTRIES.ID))
                .from(ENTRIES)
                .where(ENTRIES.ID.eq("V01").and(ENTRIES.TENANT_ID.eq("T01")))
                .fetchOne();

        assertEquals("V01", row.intoMap().get("id"));
    }

    @Test
    void insertColumnsCollectionValuesVarargsArrayMatcherMatches() {
        when(dsl.insertInto(any(Table.class)).columns(any(Collection.class)).values(any(Object[].class)).execute())
                .thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> dsl.insertInto(ENTRIES)
                .columns(List.of(ENTRIES.ID, ENTRIES.TENANT_ID, ENTRIES.ENTITY_TYPE))
                .values("V01", "T01", "engine")
                .execute());
    }

    @Test
    void insertColumnsCollectionValuesCollectionMatches() {
        when(dsl.insertInto(any(Table.class)).columns(any(Collection.class)).values(any(Collection.class)).execute())
                .thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> dsl.insertInto(ENTRIES)
                .columns(List.of(ENTRIES.ID, ENTRIES.TENANT_ID, ENTRIES.ENTITY_TYPE))
                .values(List.of("V01", "T01", "engine"))
                .execute());
    }

    @Test
    void insertColumnsCollectionValuesSingleFieldMatches() {
        when(dsl.insertInto(any(Table.class)).columns(any(Collection.class)).values(any(Object[].class)).execute())
                .thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> dsl.insertInto(ENTRIES)
                .columns(List.of(ENTRIES.ID))
                .values("V01")
                .execute());
    }

    @Test
    void updateChainMatches() {
        when(dsl.update(any(Table.class)).set(any(java.util.Map.class)).where(any(Condition.class)).execute())
                .thenReturn(1);

        int n = dsl.update(ENTRIES).set(java.util.Map.of(ENTRIES.NEW_VERSION, "2.0"))
                .where(ENTRIES.ID.eq("V01")).execute();
        assertEquals(1, n);
    }

    @Test
    void deleteChainMatches() {
        when(dsl.delete(any(Table.class)).where(any(Condition.class)).execute()).thenReturn(1);

        int n = dsl.delete(ENTRIES).where(ENTRIES.ID.eq("V01")).execute();
        assertEquals(1, n);
    }

    @Test
    void selectCountMatches() {
        when(dsl.select(any(Collection.class)).from(any(TableLike.class)).where(any(Condition.class)).fetchOne())
                .thenReturn(JooqTestData.record(java.util.Map.of("count", 3L)));

        var r = dsl.select(List.of(org.jooq.impl.DSL.count())).from(ENTRIES)
                .where(ENTRIES.TENANT_ID.eq("T01")).fetchOne();
        assertEquals(3L, r.intoMap().get("count"));
    }

    // NOT: fetchValue(String, Object...) overload'u jOOQ'da yoktur (ADR-014 not 5);
    // fetchValue(Field) da DSLContext'te overload çakışması nedeniyle mock'lanamaz —
    // ana kodda kullanılmaz; değer okuma fetchOne(...).get(0, Class) ile yapılır.

    @Test
    void joinChainMatches() {
        when(dsl.select(any(Collection.class)).from(any(TableLike.class)).join(any(TableLike.class))
                .on(any(Condition.class)).where(any(Condition.class)).fetch())
                .thenReturn(JooqTestData.records(List.of(java.util.Map.of("id", "V01"))));

        var rows = dsl.select(List.of(ENTRIES.ID)).from(ENTRIES)
                .join(ENTRIES)
                .on(ENTRIES.ID.eq(ENTRIES.ID))
                .where(ENTRIES.TENANT_ID.eq("T01"))
                .fetch();
        assertEquals(1, rows.size());
    }
}
