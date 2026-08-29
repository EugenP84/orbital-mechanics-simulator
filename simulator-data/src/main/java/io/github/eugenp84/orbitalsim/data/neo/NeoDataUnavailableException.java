package io.github.eugenp84.orbitalsim.data.neo;

/**
 * Thrown when NEO data cannot be acquired from the live service and no compatible cached dataset is available to satisfy the request (ADR-007 §5, §7).
 * <p>
 * This represents an <em>acquisition</em> failure — the external service or
 * its data was unavailable or unusable, and the cache could not cover the request.
 * It is distinct from an internal processing defect, which surfaces as an ordinary unchecked exception and is never converted into this type
 * (ADR-007 §5): masking a programming error as data-unavailability would hide exactly the kind of defect that must remain visible.
 */
public class NeoDataUnavailableException extends RuntimeException
{

    public NeoDataUnavailableException(String message)
    {
        super(message);
    }

    public NeoDataUnavailableException(String message, Throwable cause)
    {
        super(message, cause);
    }
}