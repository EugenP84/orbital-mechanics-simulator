package io.github.eugenp84.orbitalsim.data.neo;

import java.time.Instant;
import java.util.Objects;

/**
 * A single close-approach event between a near-Earth object and Earth,
 * expressed in provider-neutral terms and SI units.
 * <p>
 * This is a normalised representation (ADR-007 §3): it names quantities in the
 * simulation's own terms and carries only what the simulation needs, rather than mirroring any external provider's field names or unit conventions.
 * A provider adapter is responsible for converting its source representation — for example NeoWs's kilometre and km/s fields and Unix-epoch timestamps — into this form.
 * <p>
 * Each value is meaningful at {@code epoch}: the miss distance and relative velocity describe the geometry at the instant of closest approach, not at any orbital-element epoch. Keeping the epoch attached to the event is essential for the CNEOS validation of Objective 6, which must compare
 * simulated and published quantities at the same instant.
 *
 * @param epoch                     the instant of closest approach
 * @param missDistanceMetres        distance between the bodies at closest
 *                                  approach, in metres; positive and finite
 * @param relativeVelocityMetresPerSecond relative speed at closest approach,
 *                                  in metres per second; non-negative and
 *                                  finite
 * @param orbitingBody              the body this approach is measured relative
 *                                  to (for NeoWs close approaches, "Earth");
 *                                  never null or blank
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
            throw new IllegalArgumentException(
                    "missDistanceMetres must be positive and finite, was: " + missDistanceMetres);
        }
        if (!Double.isFinite(relativeVelocityMetresPerSecond)
                || relativeVelocityMetresPerSecond < 0.0) {
            throw new IllegalArgumentException(
                    "relativeVelocityMetresPerSecond must be non-negative and finite, was: "
                            + relativeVelocityMetresPerSecond);
        }
    }
}