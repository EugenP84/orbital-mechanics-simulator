package io.github.eugenp84.orbitalsim.data.planet;

import java.util.List;
import java.util.Objects;

/**
 * Bundled Level 2 planet catalogue together with explicit source provenance.
 *
 * @author Eugeniu PLATITA
 */
public record SolarSystemPlanetCatalogue(
        String physicalDataSource,
        String physicalDataSourceUrl,
        String classificationSourceUrl,
        String retrievedDate,
        List<SolarSystemPlanet> planets)
{
    public SolarSystemPlanetCatalogue
    {
        requireText(physicalDataSource, "physicalDataSource");
        requireText(physicalDataSourceUrl, "physicalDataSourceUrl");
        requireText(classificationSourceUrl, "classificationSourceUrl");
        requireText(retrievedDate, "retrievedDate");
        planets = List.copyOf(Objects.requireNonNull(planets, "planets must not be null"));
        if (planets.size() != 8) {
            throw new IllegalArgumentException("Level 2 catalogue must contain exactly eight planets, was: " + planets.size());
        }
    }

    private static void requireText(final String value, final String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
