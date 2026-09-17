package io.github.eugenp84.orbitalsim.data.horizons;

/**
 * Signals that the bundled Horizons experiment fixture is missing, malformed, inconsistent with its frozen acquisition contract, or contains invalid data.
 *
 * @author Eugeniu PLATITA
 */
public final class HorizonsDataFormatException extends RuntimeException {

    /**
     * Creates a format failure with explanatory context.
     *
     * @param message explanation of the invalid fixture
     */
    public HorizonsDataFormatException(final String message)
    {
        super(message);
    }

    /**
     * Creates a format failure while retaining the parsing cause.
     *
     * @param message explanation of the invalid fixture
     * @param cause   underlying parsing failure
     */
    public HorizonsDataFormatException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
