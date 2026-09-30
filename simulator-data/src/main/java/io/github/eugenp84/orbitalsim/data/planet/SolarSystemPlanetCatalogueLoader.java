package io.github.eugenp84.orbitalsim.data.planet;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Loads the bundled NASA-derived Solar-System planet catalogue used by Level 2.
 *
 * @author Eugeniu PLATITA
 */
public final class SolarSystemPlanetCatalogueLoader
{
    private static final String RESOURCE = "/planets/solar-system-planets.json";

    /**
     * Loads and validates the bundled catalogue.
     *
     * @return immutable catalogue
     * @throws IllegalStateException if the resource is missing or malformed
     */
    public SolarSystemPlanetCatalogue load()
    {
        try (InputStream input = SolarSystemPlanetCatalogueLoader.class.getResourceAsStream(RESOURCE))
        {
            if (input == null) {
                throw new IllegalStateException("Planet catalogue resource not found: " + RESOURCE);
            }

            final SolarSystemPlanetCatalogue catalogue = new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), SolarSystemPlanetCatalogue.class);
            validateCatalogue(catalogue);
            return catalogue;
        }
        catch (IOException | JsonParseException | IllegalArgumentException cause) {
            throw new IllegalStateException("Failed to load trusted planet catalogue: " + RESOURCE, cause);
        }
    }

    private static void validateCatalogue(final SolarSystemPlanetCatalogue catalogue)
    {
        if (catalogue == null) {
            throw new IllegalArgumentException("planet catalogue must not be null");
        }

        final Set<String> names = new HashSet<>();
        for (SolarSystemPlanet planet : catalogue.planets())
        {
            if (!names.add(planet.name()))
            {
                throw new IllegalArgumentException("duplicate planet in catalogue: " + planet.name());
            }
        }

        final Set<String> expected = Set.of("Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune");
        if (!names.equals(expected)) {
            throw new IllegalArgumentException("planet catalogue does not contain the canonical eight planets");
        }
    }
}
