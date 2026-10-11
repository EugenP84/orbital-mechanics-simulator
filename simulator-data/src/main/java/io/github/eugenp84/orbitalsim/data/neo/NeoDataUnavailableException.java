package io.github.eugenp84.orbitalsim.data.neo;

/**
 * Thrown when NEO data cannot be acquired from the live service and no compatible cached dataset is available to satisfy the request (ADR-007 §5, §7).
 * <p>
 * @author Eugeniu PLATITA
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