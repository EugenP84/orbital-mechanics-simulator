package io.github.eugenp84.orbitalsim.data.nasa;

/**
 * Signals that a NeoWs response body could not be understood as a well-formed feed — invalid JSON, a missing or null required field, a non-numeric numeric
 * string, or values that violate the provider-neutral contract (ADR-007 §5).
 * <p>
 * @author Eugeniu PLATITA
 */
public class NeoDataFormatException extends RuntimeException
{

    public NeoDataFormatException(String message)
    {
        super(message);
    }

    public NeoDataFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}