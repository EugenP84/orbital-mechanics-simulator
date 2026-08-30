package io.github.eugenp84.orbitalsim.data.http;

/**
 * Signals that an HTTP request did not complete — timeout, connection failure,
 * DNS failure, interrupted I/O — so no usable HTTP response was obtained.
 * <p>
 * This is distinct from a completed request that returned an error status:
 * a 429 or 500 is a {@link HttpResponseData} the server chose to send, whereas
 * this exception means the exchange never produced a response at all.
 * A caller classifies the two differently (ADR-007 §5): both may be acquisition failures, but they arrive by different routes.
 */
public class HttpTransportException extends RuntimeException
{

    public HttpTransportException(String message)
    {
        super(message);
    }

    public HttpTransportException(String message, Throwable cause)
    {
        super(message, cause);
    }
}