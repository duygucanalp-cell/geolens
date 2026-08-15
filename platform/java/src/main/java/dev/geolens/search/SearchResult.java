package dev.geolens.search;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * ES arama sonucu — Go {@code search.SearchResult} struct portu.
 * <p>{@code hits} toplam eşleşme sayısı, {@code documents} ise her hit'in
 * {@code _source} gövdesidir (Go {@code []json.RawMessage} karşılığı).
 */
public record SearchResult(int hits, List<JsonNode> documents) {
}
