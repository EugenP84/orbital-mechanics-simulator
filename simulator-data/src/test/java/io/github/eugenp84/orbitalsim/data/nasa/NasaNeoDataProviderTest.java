package io.github.eugenp84.orbitalsim.data.nasa;

import io.github.eugenp84.orbitalsim.data.http.FakeHttpTransport;
import io.github.eugenp84.orbitalsim.data.http.HttpTransportException;
import io.github.eugenp84.orbitalsim.data.neo.DataSource;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataProvider;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataResult;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataUnavailableException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NasaNeoDataProviderTest {

    private static final LocalDate DAY = LocalDate.of(2029, 4, 13);
    private static final String KEY = "test-key";

    private static String realFeedJson() throws IOException {
        try (InputStream in = NasaNeoDataProviderTest.class.getResourceAsStream(
                "/neo/neows-feed-2029-04-13.json")) {
            assertNotNull(in, "real feed fixture must be on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * A fallback spy: records whether it was consulted, and returns a CACHED
     * result. This is what lets tests prove the taxonomy — that 401/403/400
     * surface *without* consulting the fallback, and that 429/5xx/timeout do.
     */
    private static final class SpyFallback implements NeoDataProvider {
        boolean called = false;
        final boolean available;

        SpyFallback(boolean available) {
            this.available = available;
        }

        @Override
        public NeoDataResult findCloseApproaches(LocalDate start, LocalDate end) {
            called = true;
            if (!available) {
                throw new NeoDataUnavailableException("spy fallback: no data");
            }
            return new NeoDataResult(
                    List.of(), DataSource.CACHED, Instant.parse("2026-01-01T00:00:00Z"));
        }
    }

    private NasaNeoDataProvider provider(
            FakeHttpTransport transport, SpyFallback fallback) {
        return new NasaNeoDataProvider(transport, new NasaNeoWsParser(), fallback, KEY);
    }

    // --- Construction ---

    @Test
    void constructorRejectsNullsAndBlankKey() {
        FakeHttpTransport t = FakeHttpTransport.responding(200, "{}");
        NasaNeoWsParser p = new NasaNeoWsParser();
        SpyFallback f = new SpyFallback(true);

        assertThrows(NullPointerException.class,
                () -> new NasaNeoDataProvider(null, p, f, KEY));
        assertThrows(NullPointerException.class,
                () -> new NasaNeoDataProvider(t, null, f, KEY));
        assertThrows(NullPointerException.class,
                () -> new NasaNeoDataProvider(t, p, null, KEY));
        assertThrows(NullPointerException.class,
                () -> new NasaNeoDataProvider(t, p, f, null));
        assertThrows(IllegalArgumentException.class,
                () -> new NasaNeoDataProvider(t, p, f, "   "));
    }

    // --- 2xx success ---

    @Test
    void validSuccessReturnsLiveWithoutConsultingFallback() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.responding(200, realFeedJson());
        SpyFallback fallback = new SpyFallback(true);

        NeoDataResult result = provider(transport, fallback).findCloseApproaches(DAY, DAY);

        assertEquals(DataSource.LIVE, result.source());
        assertEquals(9, result.objects().size());
        assertFalse(fallback.called, "a valid live response must not consult the fallback");
    }

    @Test
    void theBuiltUriTargetsTheFeedEndpointWithDatesAndKey() throws IOException {
        FakeHttpTransport transport = FakeHttpTransport.responding(200, realFeedJson());
        provider(transport, new SpyFallback(true)).findCloseApproaches(DAY, DAY);

        //String uri = transport.lastRequestedUri.toString();
        String uri = transport.lastRequestedUri().toString();
        assertTrue(uri.startsWith("https://api.nasa.gov/neo/rest/v1/feed"));
        assertTrue(uri.contains("start_date=2029-04-13"));
        assertTrue(uri.contains("end_date=2029-04-13"));
        assertTrue(uri.contains("api_key=test-key"));
    }

    // --- malformed 2xx: fallback-eligible (ADR-007 §5 category b) ---

    @Test
    void malformedSuccessBodyFallsBackToCache() {
        FakeHttpTransport transport = FakeHttpTransport.responding(200, "{ not json");
        SpyFallback fallback = new SpyFallback(true);

        NeoDataResult result = provider(transport, fallback).findCloseApproaches(DAY, DAY);

        assertTrue(fallback.called, "a malformed 2xx body is fallback-eligible");
        assertEquals(DataSource.CACHED, result.source());
    }

    // --- transport failure and 429/5xx: fallback-eligible ---

    @Test
    void transportFailureFallsBackToCache() {
        FakeHttpTransport transport = FakeHttpTransport.failing(
                new HttpTransportException("timeout"));
        SpyFallback fallback = new SpyFallback(true);

        NeoDataResult result = provider(transport, fallback).findCloseApproaches(DAY, DAY);

        assertTrue(fallback.called);
        assertEquals(DataSource.CACHED, result.source());
    }

    @Test
    void rateLimitedFallsBackToCache() {
        FakeHttpTransport transport = FakeHttpTransport.responding(429, "");
        SpyFallback fallback = new SpyFallback(true);

        NeoDataResult result = provider(transport, fallback).findCloseApproaches(DAY, DAY);

        assertTrue(fallback.called);
        assertEquals(DataSource.CACHED, result.source());
    }

    @Test
    void serverErrorFallsBackToCache() {
        FakeHttpTransport transport = FakeHttpTransport.responding(503, "");
        SpyFallback fallback = new SpyFallback(true);

        NeoDataResult result = provider(transport, fallback).findCloseApproaches(DAY, DAY);

        assertTrue(fallback.called);
        assertEquals(DataSource.CACHED, result.source());
    }

    // --- 400/401/403: surface, NO fallback (ADR-007 §6) ---

    @Test
    void badRequestSurfacesWithoutConsultingFallback() {
        FakeHttpTransport transport = FakeHttpTransport.responding(400, "");
        SpyFallback fallback = new SpyFallback(true);

        assertThrows(NeoDataUnavailableException.class,
                () -> provider(transport, fallback).findCloseApproaches(DAY, DAY));
        assertFalse(fallback.called,
                "a 400 is a request defect and must NOT silently fall back to cache");
    }

    @Test
    void unauthorizedSurfacesWithoutConsultingFallback() {
        FakeHttpTransport transport = FakeHttpTransport.responding(401, "");
        SpyFallback fallback = new SpyFallback(true);

        assertThrows(NeoDataUnavailableException.class,
                () -> provider(transport, fallback).findCloseApproaches(DAY, DAY));
        assertFalse(fallback.called,
                "a 401 is a configuration error and must NOT silently fall back");
    }

    @Test
    void forbiddenSurfacesWithoutConsultingFallback() {
        FakeHttpTransport transport = FakeHttpTransport.responding(403, "");
        SpyFallback fallback = new SpyFallback(true);

        assertThrows(NeoDataUnavailableException.class,
                () -> provider(transport, fallback).findCloseApproaches(DAY, DAY));
        assertFalse(fallback.called);
    }

    // --- fallback itself unavailable ---

    @Test
    void whenFallbackEligibleButFallbackUnavailableBothFailuresRemainDiagnosable() {
        FakeHttpTransport transport = FakeHttpTransport.responding(500, "");
        SpyFallback fallback = new SpyFallback(false);   // cache miss

        NeoDataUnavailableException thrown = assertThrows(
                NeoDataUnavailableException.class,
                () -> provider(transport, fallback).findCloseApproaches(DAY, DAY));

        assertTrue(fallback.called, "the provider did try the fallback");

        // The suppressed exception's message retains the original live 500
        // failure, proving that the diagnostic which triggered fallback is not
        // lost when live and fallback fail together.
        assertEquals(1, thrown.getSuppressed().length);
        assertEquals("service returned status 500", thrown.getSuppressed()[0].getMessage());
    }

    // --- date validation ---

    @Test
    void validatesRequestedDates() {
        FakeHttpTransport transport = FakeHttpTransport.responding(200, "{}");
        SpyFallback fallback = new SpyFallback(true);

        assertThrows(NullPointerException.class,
                () -> provider(transport, fallback).findCloseApproaches(null, DAY));
        assertThrows(IllegalArgumentException.class,
                () -> provider(transport, fallback).findCloseApproaches(
                        LocalDate.of(2029, 4, 14), DAY));
    }

    @Test
    void anUnexpectedRedirectStatusSurfacesAndDoesNotFallBack() {
        // A 302 is neither success, nor a recognised recoverable external
        // failure, nor a diagnosable client error. It surfaces as an
        // unclassified acquisition failure rather than being masked as cached
        // data, so the integration anomaly stays visible.
        FakeHttpTransport transport = FakeHttpTransport.responding(302, "");
        SpyFallback fallback = new SpyFallback(true);

        assertThrows(NeoDataUnavailableException.class,
                () -> provider(transport, fallback).findCloseApproaches(DAY, DAY));
        assertFalse(fallback.called,
                "an unexpected status must not silently fall back to cache");
    }
}