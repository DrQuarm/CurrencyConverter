package com.currencyconverter;

import org.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches live exchange rates from ExchangeRate-API.
 *
 * OFFLINE SUPPORT:
 *  - Every successful fetch is saved to disk at:
 *    {user.home}/.currencyconverter/rates_{BASE}.json
 *  - If the network is unavailable, the last saved file is loaded instead.
 *  - RateResult.offline() tells the UI which mode is active.
 */
public class ExchangeRateService {

    private static final String BASE_URL        = "https://open.er-api.com/v6/latest/";
    private static final Duration TIMEOUT       = Duration.ofSeconds(10);
    private static final long CACHE_DURATION_MS = 60 * 60 * 1000; // 1 hour in-memory

    // Folder where offline rate files are stored
    private static final Path CACHE_DIR = Path.of(
            System.getProperty("user.home"), ".currencyconverter"
    );

    private final HttpClient httpClient;

    // In-memory cache to avoid re-fetching within the same session
    private final Map<String, CachedRates> memoryCache = new HashMap<>();

    public ExchangeRateService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        try { Files.createDirectories(CACHE_DIR); } catch (IOException ignored) {}
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public CompletableFuture<RateResult> fetchRates(String baseCurrency) {
        String base = baseCurrency.toUpperCase();

        // 1 — in-memory cache hit
        CachedRates mem = memoryCache.get(base);
        if (mem != null && !mem.isExpired()) {
            return CompletableFuture.completedFuture(
                    new RateResult(mem.rates, mem.lastUpdated, null, false)
            );
        }

        // 2 — fetch from network
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + base))
                .timeout(TIMEOUT)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200)
                        return fallbackToDisk(base, "HTTP Error " + response.statusCode());

                    JSONObject json = new JSONObject(response.body());
                    if (!"success".equals(json.optString("result")))
                        return fallbackToDisk(base,
                                "API Error: " + json.optString("error-type", "Unknown"));

                    JSONObject ratesJson = json.getJSONObject("rates");
                    Map<String, Double> rates = new HashMap<>();
                    for (String key : ratesJson.keySet())
                        rates.put(key, ratesJson.getDouble(key));

                    String lastUpdated = json.optString("time_last_update_utc", "Unknown");

                    memoryCache.put(base, new CachedRates(rates, lastUpdated));
                    saveToDisk(base, rates, lastUpdated);

                    return new RateResult(rates, lastUpdated, null, false);
                })
                .exceptionally(ex -> fallbackToDisk(base, ex.getMessage()));
    }

    // ── Disk persistence ───────────────────────────────────────────────────────

    private void saveToDisk(String base, Map<String, Double> rates, String lastUpdated) {
        try {
            JSONObject json = new JSONObject();
            json.put("base", base);
            json.put("lastUpdated", lastUpdated);
            json.put("savedAt", System.currentTimeMillis());
            JSONObject ratesJson = new JSONObject();
            rates.forEach(ratesJson::put);
            json.put("rates", ratesJson);
            Files.writeString(CACHE_DIR.resolve("rates_" + base + ".json"), json.toString(2));
        } catch (IOException ignored) {}
    }

    private RateResult fallbackToDisk(String base, String networkError) {
        Path file = CACHE_DIR.resolve("rates_" + base + ".json");
        if (!Files.exists(file))
            return new RateResult(null, null,
                    "No internet and no saved rates for " + base, true);
        try {
            JSONObject json     = new JSONObject(Files.readString(file));
            JSONObject ratesJson = json.getJSONObject("rates");
            Map<String, Double> rates = new HashMap<>();
            for (String key : ratesJson.keySet())
                rates.put(key, ratesJson.getDouble(key));
            String lastUpdated = json.optString("lastUpdated", "Unknown");
            memoryCache.put(base, new CachedRates(rates, lastUpdated));
            return new RateResult(rates, lastUpdated, null, true);
        } catch (IOException | org.json.JSONException e) {
            return new RateResult(null, null, "Cache file corrupt: " + e.getMessage(), true);
        }
    }

    // ── Inner classes ──────────────────────────────────────────────────────────

    public record RateResult(
            Map<String, Double> rates,
            String lastUpdated,
            String errorMessage,
            boolean offline
    ) {
        public boolean isSuccess() { return errorMessage == null && rates != null; }
    }

    private static class CachedRates {
        final Map<String, Double> rates;
        final String lastUpdated;
        final long fetchTimeMs;

        CachedRates(Map<String, Double> rates, String lastUpdated) {
            this.rates       = rates;
            this.lastUpdated = lastUpdated;
            this.fetchTimeMs = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - fetchTimeMs > CACHE_DURATION_MS;
        }
    }
}
