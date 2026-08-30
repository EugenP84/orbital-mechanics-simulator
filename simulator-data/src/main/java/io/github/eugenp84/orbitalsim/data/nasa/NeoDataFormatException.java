package io.github.eugenp84.orbitalsim.data.nasa;

/**
 * Signals that a NeoWs response body could not be understood as a well-formed feed — invalid JSON, a missing or null required field, a non-numeric numeric
 * string, or values that violate the provider-neutral contract (ADR-007 §5).
 * <p>
 * This is an <em>external representation / schema / data failure</em>:
 * it is fallback-eligible, and is deliberately distinct from an internal processing defect,
 * which surfaces as an ordinary unchecked exception and is never converted into this type.
 * The provider uses the type — not the stack frame — to decide fallback eligibility.
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