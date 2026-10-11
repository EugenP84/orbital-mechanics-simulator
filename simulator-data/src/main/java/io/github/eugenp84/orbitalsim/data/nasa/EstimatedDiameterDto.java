package io.github.eugenp84.orbitalsim.data.nasa;

import com.google.gson.annotations.SerializedName;

/**
 * Estimated-diameter block. The feed provides four unit systems; the parser reads {@link #meters} directly, avoiding a conversion (ADR-007 §4).
 * Package-private to the NASA adapter (ADR-007 §3). Diameter values are JSON numbers, unlike the string-typed velocity and distance fields elsewhere in this response.
 *
 * @author Eugeniu PLATITA
 */
final class EstimatedDiameterDto
{

    @SerializedName("meters")
    MinMaxDto meters;

    /** Min/max pair within one unit system. */
    static final class MinMaxDto {
        @SerializedName("estimated_diameter_min")
        Double estimatedDiameterMin;

        @SerializedName("estimated_diameter_max")
        Double estimatedDiameterMax;
    }
}