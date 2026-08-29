package io.github.eugenp84.orbitalsim.data.neo;

import java.util.List;
import java.util.Objects;

/**
 * A near-Earth object in provider-neutral form (ADR-007 §3).
 * <p>
 * This type is owned by {@code simulator-data} and named in the simulation's
 * own terms. It deliberately does not mirror any external provider's schema:
 * an adapter normalises its source representation into this shape, so that a
 * change to an external JSON schema is absorbed at the adapter boundary and
 * never reaches the rest of the application.
 * <p>
 * It carries only what downstream consumers need: a stable identity and
 * display name, a representative diameter from which a body radius can be
 * derived, the planetary-defence hazard flag, and the object's close-approach
 * events. It does <strong>not</strong> carry Keplerian orbital elements: the
 * close-approach feed does not provide them, and constructing a simulation
 * state from this data is the responsibility of a separate conversion step
 * (ADR-008), not of this acquisition-layer type.
 * <p>
 * The diameter is represented as a min/max estimate because that is the form
 * in which such estimates are known — a single figure would imply a precision
 * the underlying observation does not have. A consumer needing one value can
 * take the midpoint, but the honest range is preserved here.
 *
 * @param id                    stable provider identifier; never null or blank
 * @param name                  human-readable designation; never null or blank
 * @param estimatedDiameterMinMetres minimum estimated diameter in metres;
 *                              positive and finite
 * @param estimatedDiameterMaxMetres maximum estimated diameter in metres;
 *                              finite and not less than the minimum
 * @param potentiallyHazardous  the provider's potentially-hazardous flag
 * @param closeApproaches       close-approach events for this object, in the
 *                              order supplied; never null, defensively copied,
 *                              may be empty
 */
public record NeoObjectData(
        String id,
        String name,
        double estimatedDiameterMinMetres,
        double estimatedDiameterMaxMetres,
        boolean potentiallyHazardous,
        List<CloseApproach> closeApproaches) {

    public NeoObjectData {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(closeApproaches, "closeApproaches must not be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (!Double.isFinite(estimatedDiameterMinMetres) || estimatedDiameterMinMetres <= 0.0) {
            throw new IllegalArgumentException(
                    "estimatedDiameterMinMetres must be positive and finite, was: "
                            + estimatedDiameterMinMetres);
        }
        if (!Double.isFinite(estimatedDiameterMaxMetres)
                || estimatedDiameterMaxMetres < estimatedDiameterMinMetres) {
            throw new IllegalArgumentException(
                    "estimatedDiameterMaxMetres must be finite and >= the minimum ("
                            + estimatedDiameterMinMetres + "), was: " + estimatedDiameterMaxMetres);
        }

        // List.copyOf rejects null elements and yields an immutable list, so
        // the published collection cannot be mutated through the caller's
        // reference (same discipline as SimulationSnapshot, ADR-006 §6)
        closeApproaches = List.copyOf(closeApproaches);
    }

    /**
     * Midpoint of the estimated diameter range, in metres.
     * <p>
     * Computed as {@code min + 0.5 * (max - min)} rather than
     * {@code 0.5 * (min + max)} so that the intermediate sum cannot overflow:
     * both bounds are finite by construction, but two finite doubles can sum
     * to infinity. Since {@code max >= min >= 0}, the difference cannot exceed
     * {@code max}, so the result stays finite and within {@code [min, max]}.
     */
    public double estimatedDiameterMidpointMetres() {
        return estimatedDiameterMinMetres
                + 0.5 * (estimatedDiameterMaxMetres - estimatedDiameterMinMetres);
    }
}