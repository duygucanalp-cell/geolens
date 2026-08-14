package dev.geolens.testutil;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * jOOQ mock sonuçları için test yardımcıları — ADR-014.
 * <p>Kontrolör katmanı plain SQL'i {@code dsl.fetch/fetchOne/execute} ile çalıştırır;
 * unit testler {@code DSLContext} mock'layıp {@link #records(List)}/{@link #record(Map)}
 * ile hazırlanan jOOQ {@link Result}/{@link Record} döndürür. {@code intoMaps()} ile
 * aynı anahtar adları korunur (eski JdbcTemplate row-mapping davranışı).
 */
public final class JooqTestData {

    private static final DSLContext CTX = DSL.using(SQLDialect.POSTGRES);

    private JooqTestData() {
    }

    @SafeVarargs
    public static Result<Record> records(Map<String, Object>... rows) {
        return records(List.of(rows));
    }

    public static Result<Record> records(List<Map<String, Object>> rows) {
        Result<Record> result = CTX.newResult(new Field<?>[0]);
        for (Map<String, Object> row : rows) {
            result.add(record(row));
        }
        return result;
    }

    /** Tek sütunlu kayıt — {@code fetchOne} ile tek değer (value/map) mock'lamak için. */
    public static Record record(Object singleValue) {
        return record(Map.of("value", singleValue));
    }

    public static Record record(Map<String, Object> row) {
        List<Field<Object>> fields = new ArrayList<>();
        for (String key : row.keySet()) {
            fields.add(DSL.field(key, Object.class));
        }
        Record rec = CTX.newRecord(fields);
        row.forEach((k, v) -> rec.set(DSL.field(k, Object.class), v));
        return rec;
    }
}
