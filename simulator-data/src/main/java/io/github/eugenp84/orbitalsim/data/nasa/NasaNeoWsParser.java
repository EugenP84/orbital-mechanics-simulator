package io.github.eugenp84.orbitalsim.data.nasa;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.github.eugenp84.orbitalsim.data.neo.CloseApproach;
import io.github.eugenp84.orbitalsim.data.neo.NeoObjectData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a NeoWs {@code /feed} JSON body into provider-neutral {@link NeoObjectData} values.
 * <p>
 * @author Eugeniu PLATITA
 */
public final class NasaNeoWsParser {

    private static final double METRES_PER_KILOMETRE = 1000.0;

    private final Gson gson = new Gson();

    /**
     * Parses a feed response body into provider-neutral objects.
     *
     * @param json the raw JSON body, from a live response or a cached resource
     * @return the objects contained in the feed, flattened across all dates
     * @throws NeoDataFormatException if the body is not a well-formed feed response (an external representation failure)
     */
    public List<NeoObjectData> parseFeed(String json)
    {
        if (json == null)
        {
            throw new NeoDataFormatException("feed body was null");
        }

        FeedResponseDto feed;
        try
        {
            feed = gson.fromJson(json, FeedResponseDto.class);
        }
        catch (JsonSyntaxException e)
        {
            throw new NeoDataFormatException("feed body was not valid JSON", e);
        }

        if (feed == null || feed.nearEarthObjects == null)
        {
            throw new NeoDataFormatException("feed body did not contain near_earth_objects");
        }

        List<NeoObjectData> objects = new ArrayList<>();
        for (List<NeoDto> perDate : feed.nearEarthObjects.values()) {
            if (perDate == null) {
                throw new NeoDataFormatException("a date bucket was null");
            }
            for (NeoDto neo : perDate) {
                objects.add(normalise(neo));
            }
        }
        return objects;
    }

    private NeoObjectData normalise(NeoDto neo) {
        if (neo == null) {
            throw new NeoDataFormatException("a NEO entry was null");
        }
        require(neo.id != null, "id missing");
        require(neo.name != null, "name missing");
        require(neo.isPotentiallyHazardousAsteroid != null,
                "is_potentially_hazardous_asteroid missing");
        require(neo.estimatedDiameter != null && neo.estimatedDiameter.meters != null,
                "estimated_diameter.meters missing");
        require(neo.estimatedDiameter.meters.estimatedDiameterMin != null,
                "estimated_diameter_min missing");
        require(neo.estimatedDiameter.meters.estimatedDiameterMax != null,
                "estimated_diameter_max missing");
        require(neo.closeApproachData != null, "close_approach_data missing");

        List<CloseApproach> approaches = new ArrayList<>();
        for (CloseApproachDto ca : neo.closeApproachData) {
            approaches.add(normalise(ca));
        }

        try {
            return new NeoObjectData(
                    neo.id,
                    neo.name,
                    neo.estimatedDiameter.meters.estimatedDiameterMin,
                    neo.estimatedDiameter.meters.estimatedDiameterMax,
                    neo.isPotentiallyHazardousAsteroid,
                    approaches);
        } catch (IllegalArgumentException e) {
            // The provider-neutral contract rejected NASA's values (e.g. non-positive diameter).
            throw new NeoDataFormatException("NEO " + neo.id + " failed provider-neutral validation: " + e.getMessage(), e);
        }
    }

    private CloseApproach normalise(CloseApproachDto ca) {
        if (ca == null) {
            throw new NeoDataFormatException("a close_approach_data entry was null");
        }
        require(ca.epochDateCloseApproach != null, "epoch_date_close_approach missing");
        require(ca.relativeVelocity != null
                        && ca.relativeVelocity.kilometersPerSecond != null,
                "relative_velocity.kilometers_per_second missing");
        require(ca.missDistance != null && ca.missDistance.kilometers != null,
                "miss_distance.kilometers missing");
        require(ca.orbitingBody != null, "orbiting_body missing");

        double kmPerSecond = parseNumber(ca.relativeVelocity.kilometersPerSecond, "relative_velocity.kilometers_per_second");
        double km = parseNumber(ca.missDistance.kilometers, "miss_distance.kilometers");

        try {
            return new CloseApproach(
                    Instant.ofEpochMilli(ca.epochDateCloseApproach),
                    km * METRES_PER_KILOMETRE,
                    kmPerSecond * METRES_PER_KILOMETRE,
                    ca.orbitingBody);
        } catch (IllegalArgumentException e) {
            throw new NeoDataFormatException("close approach failed provider-neutral validation: " + e.getMessage(), e);
        }
    }

    private static double parseNumber(String value, String field)
    {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new NeoDataFormatException(field + " was not a number: \"" + value + "\"", e);
        }
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new NeoDataFormatException(message);
        }
    }
}