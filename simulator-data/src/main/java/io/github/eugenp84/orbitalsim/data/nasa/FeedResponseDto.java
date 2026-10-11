package io.github.eugenp84.orbitalsim.data.nasa;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Top-level NeoWs {@code /feed} response. Package-private to the NASA adapter so this NASA-specific representation cannot escape it (ADR-007 §3);
 * it lives in the adapter's own package, not a subpackage, because package-private access does not reach across package boundaries.
 * <p>
 * {@code near_earth_objects} is a JSON object keyed by date string, each value an array of {@link NeoDto}. Gson deserialises it into a Map.
 *
 * @author Eugeniu PLATITA
 */
final class FeedResponseDto
{

    @SerializedName("element_count")
    int elementCount;

    @SerializedName("near_earth_objects")
    Map<String, List<NeoDto>> nearEarthObjects;
}