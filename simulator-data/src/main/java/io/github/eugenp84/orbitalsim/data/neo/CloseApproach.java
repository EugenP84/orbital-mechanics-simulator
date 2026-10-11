package io.github.eugenp84.orbitalsim.data.neo;

import java.time.Instant;
import java.util.Objects;

/**
 * A single close-approach event between a near-Earth object and Earth,
 * expressed in provider-neutral terms and SI units.
 * <p>
 *
 * @param epoch                     the instant of closest approach
 * @param missDistanceMetres        distance between the bodies at closest approach, in metres; positive and finite
 * @param relativeVelocityMetresPerSecond relative speed at closest approach, in metres per second; non-negative and finite
 * @param orbitingBody              the body this approach is measured relative to (for NeoWs close approaches, "Earth"); never null or blank
 *
 * @author Eugeniu PLATITA
 */
public record CloseApproach(
        Instant epoch,
        double missDistanceMetres,
        double relativeVelocityMetresPerSecond,
        String orbitingBody) {

    public CloseApproach
    {
        Objects.requireNonNull(epoch, "epoch must not be null");
        Objects.requireNonNull(orbitingBody, "orbitingBody must not be null");

        if (orbitingBody.isBlank()) {
            throw new IllegalArgumentException("orbitingBody must not be blank");
        }
        if (!Double.isFinite(missDistanceMetres) || missDistanceMetres <= 0.0) {
            throw new IllegalArgumentException("missDistanceMetres must be positive and finite, was: " + missDistanceMetres);
        }
        if (!Double.isFinite(relativeVelocityMetresPerSecond) || relativeVelocityMetresPerSecond < 0.0) {
            throw new IllegalArgumentException("relativeVelocityMetresPerSecond must be non-negative and finite, was: " + relativeVelocityMetresPerSecond);
        }
    }
}