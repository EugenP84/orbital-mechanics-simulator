package io.github.eugenp84.orbitalsim.data.nasa;

import io.github.eugenp84.orbitalsim.data.http.HttpResponseData;
import io.github.eugenp84.orbitalsim.data.http.HttpTransport;
import io.github.eugenp84.orbitalsim.data.http.HttpTransportException;
import io.github.eugenp84.orbitalsim.data.neo.DataSource;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataProvider;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataResult;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataUnavailableException;
import io.github.eugenp84.orbitalsim.data.neo.NeoObjectData;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A live {@link NeoDataProvider} backed by NASA's NeoWs {@code /feed} endpoint,
 * with a fallback provider consulted on fallback-eligible acquisition failures (ADR-007 §2, §5, §6).
 * <p>
 * There is deliberately no blanket {@code status >= 400 -> fallback} rule:
 * that would silently turn an authentication or configuration mistake into an apparently successful cached operation, violating the provenance and failure rules.
 *
 * @author Eugeniu PLATITA
 */
public final class NasaNeoDataProvider implements NeoDataProvider
{

    private static final String FEED_BASE = "https://api.nasa.gov/neo/rest/v1/feed";
    private final HttpTransport transport;
    private final NasaNeoWsParser parser;
    private final NeoDataProvider fallback;
    private final String apiKey;

    /**
     * @param transport the HTTP transport seam
     * @param parser    the shared NeoWs parser
     * @param fallback  the provider consulted on fallback-eligible failures
     *                  (typically a {@code BundledNeoDataProvider})
     * @param apiKey    the NASA API key, supplied by the application layer from configuration; never null or blank
     * @throws NullPointerException     if any collaborator is null
     * @throws IllegalArgumentException if the API key is blank
     */
    public NasaNeoDataProvider(
            HttpTransport transport,
            NasaNeoWsParser parser,
            NeoDataProvider fallback,
            String apiKey) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
        Objects.requireNonNull(apiKey, "apiKey must not be null");
        if (apiKey.isBlank()) {
            // Fail locally and immediately rather than letting a malformed
            // request reach the transport and return a confusing 401/403.
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        this.apiKey = apiKey;
    }

    @Override
    public NeoDataResult findCloseApproaches(LocalDate startDate, LocalDate endDate)
    {
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate (" + endDate + ") must not be before startDate (" + startDate + ")");
        }

        try
        {
            return fetchLive(startDate, endDate);
        }
        catch (AcquisitionFailure liveFailure) {
            // Fallback-eligible: the external service or its data failed.
            // Try the fallback, which enforces its own query-compatibility.
            try
            {
                return fallback.findCloseApproaches(startDate, endDate);
            }
            catch (NeoDataUnavailableException fallbackFailure) {
                // Both paths failed. Surface the fallback's failure, but retain the original live failure as suppressed so the reason we fell back at all is not lost.
                // The two are independent failures, not cause-and-effect, so addSuppressed is correct rather than initCause.
                // The catch is deliberately narrow: a defect *in* the fallback surfaces as itself, without the live cause attached.
                fallbackFailure.addSuppressed(liveFailure);
                throw fallbackFailure;
            }// end try-catch block ---------------------------===================
        }
    }

    /**
     * Attempts the live fetch. Throws {@link AcquisitionFailure} for every fallback-eligible condition; surfaces configuration/request errors directly as unchecked exceptions.
     */
    private NeoDataResult fetchLive(LocalDate startDate, LocalDate endDate)
    {
        HttpResponseData response;
        try {
            response = transport.get(buildFeedUri(startDate, endDate));
        } catch (HttpTransportException e) {
            // Request never completed — a positively-recognised recoverable external failure. Fallback-eligible.
            throw new AcquisitionFailure("transport failure", e);
        }

        int status = response.statusCode();

        // Known success.
        if (status >= 200 && status < 300) {
            List<NeoObjectData> objects;
            try {
                objects = parser.parseFeed(response.body());
            } catch (NeoDataFormatException e) {
                // A 2xx with a malformed/schema-invalid body is an external representation failure
                throw new AcquisitionFailure("malformed successful response", e);
            }
            return new NeoDataResult(objects, DataSource.LIVE, Instant.now());
        }

        // Known recoverable external failure: rate limiting or server error.
        if (status == 429 || (status >= 500 && status < 600)) {
            throw new AcquisitionFailure("service returned status " + status, null);
        }

        // Known caller/configuration failure: 400 bad request, 401/403 auth/config, other 4xx. Surface — never silently fall back (ADR-007 §6).
        if (status >= 400 && status < 500) {
            throw new NeoDataUnavailableException("NeoWs request failed with status " + status + " (configuration or request error; not a fallback condition)");
        }

        // Unknown/unclassified condition (1xx / 3xx / nonstandard). We do not infer a cause, and we do not conceal an integration anomaly behind cached data:
        // fallback is gated on positively-classified recoverable failure, not on the mere absence of a recognised fatal condition.
        throw new NeoDataUnavailableException("NeoWs returned an unexpected status " + status + " (cause unclassified; not a fallback condition)");
    }

    private URI buildFeedUri(LocalDate startDate, LocalDate endDate) {
        String query = "start_date=" + enc(startDate.toString())
                + "&end_date=" + enc(endDate.toString())
                + "&api_key=" + enc(apiKey);
        return URI.create(FEED_BASE + "?" + query);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Internal marker for a fallback-eligible acquisition failure.
     */
    private static final class AcquisitionFailure extends RuntimeException
    {
        AcquisitionFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}