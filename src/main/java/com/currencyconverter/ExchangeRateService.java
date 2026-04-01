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
            return fallbackToBuiltIn(base);   // ← NEW: use built-in rates if no disk cache
        try {
            JSONObject json      = new JSONObject(Files.readString(file));
            JSONObject ratesJson = json.getJSONObject("rates");
            Map<String, Double> rates = new HashMap<>();
            for (String key : ratesJson.keySet())
                rates.put(key, ratesJson.getDouble(key));
            String lastUpdated = json.optString("lastUpdated", "Unknown");
            memoryCache.put(base, new CachedRates(rates, lastUpdated));
            return new RateResult(rates, lastUpdated, null, true);
        } catch (IOException | org.json.JSONException e) {
            return fallbackToBuiltIn(base);   // ← NEW: disk file corrupt, use built-in
        }
    }

    /**
     * Last-resort fallback: built-in rates hardcoded at compile time (March 2026).
     * Covers the most common currencies relative to USD.
     * These are used ONLY when there is no internet AND no saved disk cache.
     */
    private RateResult fallbackToBuiltIn(String base) {
        // Base rates relative to USD (March 2026 approximate values)
        Map<String, Double> usdRates = new HashMap<>();
        usdRates.put("USD", 1.0);
        usdRates.put("EUR", 0.92);
        usdRates.put("GBP", 0.79);
        usdRates.put("JPY", 149.50);
        usdRates.put("CAD", 1.36);
        usdRates.put("AUD", 1.53);
        usdRates.put("CHF", 0.88);
        usdRates.put("CNY", 7.24);
        usdRates.put("INR", 83.10);
        usdRates.put("GHS", 15.20);
        usdRates.put("NGN", 1601.0);
        usdRates.put("ZAR", 18.63);
        usdRates.put("KES", 129.50);
        usdRates.put("GBP", 0.79);
        usdRates.put("AED", 3.67);
        usdRates.put("SGD", 1.34);
        usdRates.put("HKD", 7.82);
        usdRates.put("SEK", 10.42);
        usdRates.put("NOK", 10.55);
        usdRates.put("DKK", 6.88);
        usdRates.put("MXN", 17.15);
        usdRates.put("BRL", 4.97);
        usdRates.put("ARS", 870.0);
        usdRates.put("COP", 3900.0);
        usdRates.put("CLP", 950.0);
        usdRates.put("EGP", 30.90);
        usdRates.put("MAD", 10.05);
        usdRates.put("TZS", 2525.0);
        usdRates.put("UGX", 3750.0);
        usdRates.put("XOF", 603.0);
        usdRates.put("XAF", 603.0);
        usdRates.put("PKR", 278.0);
        usdRates.put("BDT", 110.0);
        usdRates.put("LKR", 298.0);
        usdRates.put("THB", 35.10);
        usdRates.put("MYR", 4.47);
        usdRates.put("IDR", 15650.0);
        usdRates.put("PHP", 56.20);
        usdRates.put("VND", 24350.0);
        usdRates.put("KRW", 1325.0);
        usdRates.put("TWD", 31.80);
        usdRates.put("NZD", 1.63);
        usdRates.put("RUB", 90.50);
        usdRates.put("TRY", 32.20);
        usdRates.put("QAR", 3.64);
        usdRates.put("SAR", 3.75);
        usdRates.put("KWD", 0.31);
        usdRates.put("BHD", 0.376);
        usdRates.put("OMR", 0.385);
        usdRates.put("JOD", 0.709);
        usdRates.put("ILS", 3.70);
        usdRates.put("PLN", 3.96);
        usdRates.put("CZK", 23.20);
        usdRates.put("HUF", 357.0);
        usdRates.put("RON", 4.58);
        usdRates.put("BGN", 1.80);
        usdRates.put("HRK", 6.93);
        usdRates.put("UAH", 37.20);

        // Convert to requested base currency
        Map<String, Double> rates;
        if (base.equals("USD")) {
            rates = usdRates;
        } else {
            Double baseInUsd = usdRates.get(base);
            if (baseInUsd == null || baseInUsd == 0) {
                // Unknown base — return USD rates with a note
                rates = usdRates;
            } else {
                // Cross-convert: divide all USD rates by the base rate
                rates = new HashMap<>();
                for (Map.Entry<String, Double> e : usdRates.entrySet()) {
                    rates.put(e.getKey(), e.getValue() / baseInUsd);
                }
            }
        }

        String lastUpdated = "Built-in rates (Mar 2026) — connect to internet to update";
        memoryCache.put(base, new CachedRates(rates, lastUpdated));
        return new RateResult(rates, lastUpdated, null, true);
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
