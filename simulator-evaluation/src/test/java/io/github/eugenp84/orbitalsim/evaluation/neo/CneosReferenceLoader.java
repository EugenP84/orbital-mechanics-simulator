package io.github.eugenp84.orbitalsim.evaluation.neo;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Loads and validates the preserved JPL CAD reference used for independent Apophis close-approach evaluation.
 *
 * @author Eugeniu PLATITA
 */
public final class CneosReferenceLoader
{

    private static final double ASTRONOMICAL_UNIT_METRES = 149_597_870_700.0;
    private static final double METRES_PER_KILOMETRE = 1_000.0;
    private static final String RESOURCE = "/neo/apophis-2029-cneos-reference.json";

    /**
     * Loads the version-controlled CNEOS reference and normalises it to SI.
     *
     * @return validated close-approach reference
     */
    public CneosCloseApproachReference load() {
        CneosReferenceFixture fixture = readFixture();
        validateProvenance(fixture);

        return new CneosCloseApproachReference(
                fixture.source(),
                fixture.apiVersion(),
                fixture.designation(),
                fixture.orbitId(),
                fixture.approachBody(),
                parseFinite(fixture.jdTdb(), "jdTdb"),
                auToMetres(fixture.nominalDistanceAu(), "nominalDistanceAu"),
                auToMetres(fixture.minimum3SigmaDistanceAu(), "minimum3SigmaDistanceAu"),
                auToMetres(fixture.maximum3SigmaDistanceAu(), "maximum3SigmaDistanceAu"),
                kilometresPerSecondToSi(fixture.relativeVelocityKmPerSecond(),"relativeVelocityKmPerSecond"),
                fixture.timeUncertainty3Sigma());
    }

    /**
     * Converts an astronomical-unit string to metres.
     *
     * @param value source value
     * @param name field name
     * @return SI distance
     */
    private static double auToMetres(final String value, final String name)
    {
        return parseFinite(value, name) * ASTRONOMICAL_UNIT_METRES;
    }

    /**
     * Converts a km/s string to m/s.
     *
     * @param value source value
     * @param name field name
     * @return SI speed
     */
    private static double kilometresPerSecondToSi(final String value, final String name) {
        return parseFinite(value, name) * METRES_PER_KILOMETRE;
    }

    /**
     * Parses one finite source number.
     *
     * @param value source string
     * @param name field name
     * @return finite double
     */
    private static double parseFinite(final String value, final String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        final double parsed;
        try
        {
            parsed = Double.parseDouble(value);
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be numeric", exception);
        }
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return parsed;
    }

    /**
     * Reads the immutable JSON fixture from the test classpath.
     *
     * @return raw fixture
     */
    private static CneosReferenceFixture readFixture()
    {
        try (InputStream stream = CneosReferenceLoader.class.getResourceAsStream(RESOURCE))
        {
            if (stream == null) {
                throw new IllegalStateException("Missing CNEOS reference resource: " + RESOURCE);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
            {
                CneosReferenceFixture fixture = new Gson().fromJson(reader, CneosReferenceFixture.class);
                if (fixture == null) {
                    throw new IllegalArgumentException("CNEOS reference resource is empty");
                }
                return fixture;
            }
        } catch (IOException exception)
        {
            throw new IllegalStateException("Unable to read CNEOS reference resource", exception);
        }
        catch (JsonParseException exception) {
            throw new IllegalArgumentException("Malformed CNEOS reference JSON", exception);
        }
    }

    /**
     * Enforces the frozen provenance expected by ADR-011 external validation.
     *
     * @param fixture raw fixture
     */
    private static void validateProvenance(final CneosReferenceFixture fixture)
    {
        requireEquals("NASA/JPL SBDB Close Approach Data API", fixture.source(), "source");
        requireEquals("1.5", fixture.apiVersion(), "apiVersion");
        requireEquals("99942", fixture.designation(), "designation");
        requireEquals("206", fixture.orbitId(), "orbitId");
        requireEquals("Earth", fixture.approachBody(), "approachBody");
        requireEquals("TDB", fixture.timeScale(), "timeScale");
        requireEquals("2029-Apr-13 21:46", fixture.calendarTdb(), "calendarTdb");
    }

    /**
     * Requires exact fixture provenance text.
     *
     * @param expected expected value
     * @param actual actual value
     * @param name field name
     */
    private static void requireEquals(
            final String expected,
            final String actual,
            final String name)
    {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(name + " must be '" + expected + "', was: " + actual);
        }
    }
}
