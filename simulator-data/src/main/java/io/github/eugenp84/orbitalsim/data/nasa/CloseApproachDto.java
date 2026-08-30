package io.github.eugenp84.orbitalsim.data.nasa;

import com.google.gson.annotations.SerializedName;

/**
 * One close-approach record. Package-private to the NASA adapter (ADR-007 §3).
 * <p>
 * The field types reflect NASA's actual JSON: {@code epoch_date_close_approach} is a numeric Unix-millisecond timestamp,
 * but the velocity and distance magnitudes are delivered as JSON <em>strings</em>.
 * They are held as String here and parsed to double in the normaliser,
 * so a malformed numeric string becomes a classified acquisition failure rather than a Gson error.
 */
final class CloseApproachDto {

    @SerializedName("epoch_date_close_approach")
    Long epochDateCloseApproach;

    @SerializedName("relative_velocity")
    RelativeVelocityDto relativeVelocity;

    @SerializedName("miss_distance")
    MissDistanceDto missDistance;

    @SerializedName("orbiting_body")
    String orbitingBody;

    /** Relative-velocity block. values are JSON strings. */
    static final class RelativeVelocityDto
    {
        @SerializedName("kilometers_per_second")
        String kilometersPerSecond;
    }

    /** Miss-distance block; values are JSON strings. */
    static final class MissDistanceDto
    {
        @SerializedName("kilometers")
        String kilometers;
    }
}