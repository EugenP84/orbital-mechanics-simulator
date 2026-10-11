package io.github.eugenp84.orbitalsim.data.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * The production {@link HttpTransport}, backed by {@link java.net.http.HttpClient}.
 * This is the only class in the module that performs real network I/O.
 * <p>
 * It translates a completed exchange into {@link HttpResponseData}, maps a request that never completed (timeout, connection failure, I/O error) to
 * {@link HttpTransportException} — the fallback-eligible transport failure the provider taxonomy relies on — and maps interruption to
 * {@link HttpTransportInterruptedException}, which surfaces rather than falling back, because cancellation is not service unavailability.
 * <p>
 * The request URI carries the API key as a query parameter, so a leaked URI may expose the key.
 * This class therefore never deliberately includes the URI in its own exception messages.
 * Original exceptions are retained as causes for their diagnostic value;
 * because this class cannot guarantee that a JDK or network exception's own message never echoes the URI,
 * callers and logging configuration must avoid exposing exception details or request URIs to untrusted output.
 *
 * @author Eugeniu PLATITA
 */
public final class JdkHttpTransport implements HttpTransport
{

    private final HttpClient client;
    private final Duration requestTimeout;

    /** A transport with sensible default connect/request timeouts. */
    public JdkHttpTransport()
    {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                Duration.ofSeconds(20));
    }

    /**
     * @param client         the HttpClient to use
     * @param requestTimeout per-request timeout; must be positive
     * @throws NullPointerException     if a collaborator is null
     * @throws IllegalArgumentException if the timeout is zero or negative
     */
    public JdkHttpTransport(HttpClient client, Duration requestTimeout) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            // Fail at construction: HttpRequest.Builder.timeout requires a
            // positive duration, so validate here rather than deferring the
            // error to request time.
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        this.requestTimeout = requestTimeout;
    }

    @Override
    public HttpResponseData get(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .GET()
                .build();

        try
        {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            return new HttpResponseData(response.statusCode(), body);
        }
        catch (IOException e)
        {
            // Request did not complete (connection/timeout/I/O) — fallback-
            // eligible. Message deliberately omits the URI; cause is retained.
            throw new HttpTransportException("HTTP request did not complete (I/O failure)", e);
        }
        catch (InterruptedException e)
        {
            // Preserve the interrupt and surface it: cancellation is a
            // control-flow signal, not a recoverable acquisition failure, so it
            // must NOT be routed to fallback (see HttpTransportInterruptedException).
            Thread.currentThread().interrupt();
            throw new HttpTransportInterruptedException("HTTP request was interrupted", e);
        }
    }
}