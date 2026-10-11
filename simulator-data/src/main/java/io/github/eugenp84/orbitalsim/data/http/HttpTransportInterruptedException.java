package io.github.eugenp84.orbitalsim.data.http;

/**
 * Signals that an HTTP request was interrupted — the calling thread received an interrupt while the request was in progress.
 * <p>
 * This is deliberately <strong>not</strong> a subtype of {@link HttpTransportException}. Interruption is a cancellation / control-flow signal,
 * not evidence that the remote service is temporarily unavailable.
 * The provider's fallback logic catches only {@code HttpTransportException}, so interruption surfaces rather than being misclassified as a recoverable acquisition failure and routed to the cache (ADR-007 §5: classify by cause).
 * A thread asked to stop must not continue doing fallback work.
 *
 * @author Eugeniu PLATITA
 */
public final class HttpTransportInterruptedException extends RuntimeException
{

    public HttpTransportInterruptedException(String message, Throwable cause)
    {
        super(message, cause);
    }
}