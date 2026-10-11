package io.github.eugenp84.orbitalsim.data.nasa;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/** One NEO in the feed. Package-private to the NASA adapter (ADR-007 §3). */
final class NeoDto
{

    @SerializedName("id")
    String id;

    @SerializedName("neo_reference_id")
    String neoReferenceId;

    @SerializedName("name")
    String name;

    @SerializedName("absolute_magnitude_h")
    Double absoluteMagnitudeH;

    @SerializedName("estimated_diameter")
    EstimatedDiameterDto estimatedDiameter;

    @SerializedName("is_potentially_hazardous_asteroid")
    Boolean isPotentiallyHazardousAsteroid;

    @SerializedName("close_approach_data")
    List<CloseApproachDto> closeApproachData;
}