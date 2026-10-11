package io.github.eugenp84.orbitalsim.data.horizons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads and validates the deterministic JPL Horizons fixture used to initialise the canonical Apophis 2029 experiment (ADR-011).
 * <p>
 * The fixture stores authoritative source components as JSON strings so their published decimal/scientific notation is preserved in version control.
 * This loader is the normalisation boundary: it parses those values, verifies the frozen provenance contract, converts km to m and km/s to m/s,
 * and emits immutable {@link StateVector} values for simulation composition.
 *
 * @author Eugeniu PLATITA
 */
public final class HorizonsExperimentLoader
{

    private static final double KILOMETRES_TO_METRES = 1000.0;
    private static final String EXPECTED_APOPHIS_TARGET = "99942 Apophis (2004 MN4)";
    private static final String EXPECTED_CENTER = "Sun (10)";
    private static final String EXPECTED_EARTH_TARGET = "Earth (399)";
    private static final String EXPECTED_OUTPUT_TYPE = "GEOMETRIC cartesian states";
    private static final String EXPECTED_POSITION_UNIT = "km";
    private static final String EXPECTED_REFERENCE_FRAME = "Ecliptic of J2000.0";
    private static final String EXPECTED_SYSTEM = "NASA/JPL Horizons";
    private static final String EXPECTED_TIME_SCALE = "TDB";
    private static final String EXPECTED_VELOCITY_UNIT = "km/s";
    private static final String DEFAULT_RESOURCE = "/neo/apophis-2029-horizons.json";

    private final String resourcePath;

    /**
     * Creates a loader for the canonical bundled L4 fixture.
     */
    public HorizonsExperimentLoader() {
        this(DEFAULT_RESOURCE);
    }

    /**
     * Creates a loader for a specified classpath fixture, primarily to permit deterministic malformed-fixture tests without changing production code.
     *
     * @param resourcePath absolute classpath resource path
     */
    public HorizonsExperimentLoader(final String resourcePath)
    {
        this.resourcePath = Objects.requireNonNull(resourcePath,
                "resourcePath must not be null");
        if (resourcePath.isBlank()) {
            throw new IllegalArgumentException("resourcePath must not be blank");
        }
    }

    /**
     * Loads, validates and normalises the configured Horizons experiment fixture.
     *
     * @return immutable experiment data in SI units
     * @throws HorizonsDataFormatException if the fixture cannot be trusted
     */
    public HorizonsExperimentData load()
    {
        String json = loadResource();
        JsonObject root = parseRoot(json);
        HorizonsProvenance provenance = parseProvenance(root);
        validateProvenance(provenance);
        List<HorizonsEpochState> states = parseStates(root);
        return new HorizonsExperimentData(provenance, states);
    }

    /**
     * Reads the configured classpath resource as UTF-8 text.
     *
     * @return raw JSON fixture
     */
    private String loadResource()
    {
        try (InputStream input = HorizonsExperimentLoader.class
                .getResourceAsStream(resourcePath))
        {
            if (input == null)
            {
                throw new HorizonsDataFormatException("Horizons fixture resource not found: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException cause) {
            throw new HorizonsDataFormatException("Failed to read Horizons fixture: " + resourcePath, cause);
        }
    }//-------- end of loadResource() ---------------------------------------

    /**
     * Parses one source numeric string and verifies finiteness.
     *
     * @param value source text
     * @param field field path used in error reporting
     * @return parsed finite value
     */
    private static double parseFiniteDouble(final String value, final String field)
    {
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException cause) {
            throw new HorizonsDataFormatException(field + " was not numeric: " + value, cause);
        }
        if (!Double.isFinite(parsed)) {
            throw new HorizonsDataFormatException(field + " must be finite");
        }
        return parsed;
    }

    /**
     * Parses one paired Earth/Apophis state at a common TDB epoch.
     *
     * @param element JSON state element
     * @param index   state index used in error reporting
     * @return normalised paired state
     */
    private static HorizonsEpochState parseEpochState(
            final JsonElement element,
            final int index)
    {
        if (!element.isJsonObject())
        {
            throw new HorizonsDataFormatException("states[" + index + "] must be an object");
        }
        JsonObject state = element.getAsJsonObject();
        String prefix = "states[" + index + "]";
        double jdTdb = parseFiniteDouble(requireString(state, "jdTdb", prefix),prefix + ".jdTdb");
        StateVector apophis = parseStateVector(requireObject(state, "apophis", prefix), prefix + ".apophis");
        StateVector earth = parseStateVector(requireObject(state, "earth", prefix), prefix + ".earth");
        return new HorizonsEpochState(apophis, earth, jdTdb);
    }

    /**
     * Parses the provenance object without weakening the source metadata into
     * simulator-core concepts.
     *
     * @param root fixture root
     * @return parsed provenance
     */
    private static HorizonsProvenance parseProvenance(final JsonObject root) {
        JsonObject source = requireObject(root, "source", "root");
        return new HorizonsProvenance(
                requireString(source, "apiVersion", "source"),
                requireString(source, "apophisSolution", "source"),
                requireString(source, "apophisTarget", "source"),
                requireString(source, "center", "source"),
                requireString(source, "earthTarget", "source"),
                requireString(source, "outputType", "source"),
                requireString(source, "positionUnit", "source"),
                requireString(source, "referenceFrame", "source"),
                requireString(source, "system", "source"),
                requireString(source, "timeScale", "source"),
                requireString(source, "velocityUnit", "source"));
    }

    /**
     * Parses the fixture root and classifies malformed JSON as an external-data
     * format failure.
     *
     * @param json raw fixture JSON
     * @return root JSON object
     */
    private static JsonObject parseRoot(final String json)
    {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject())
            {
                throw new HorizonsDataFormatException("Horizons fixture root must be an object");
            }
            return root.getAsJsonObject();

        } catch (JsonParseException cause) {
            throw new HorizonsDataFormatException("Horizons fixture was not valid JSON", cause);
        }
    }

    /**
     * Parses every candidate epoch and relies on the experiment record to enforce non-empty, strictly increasing epoch order.
     *
     * @param root fixture root
     * @return candidate states in fixture order
     */
    private static List<HorizonsEpochState> parseStates(final JsonObject root)
    {
        JsonElement statesElement = root.get("states");
        if (statesElement == null || !statesElement.isJsonArray()) {
            throw new HorizonsDataFormatException("root.states must be an array");
        }
        JsonArray statesArray = statesElement.getAsJsonArray();
        List<HorizonsEpochState> states = new ArrayList<>();
        for (int i = 0; i < statesArray.size(); i++)
        {
            states.add(parseEpochState(statesArray.get(i), i));
        }
        return states;
    }// end of parseStates() -----------=========================

    /**
     * Parses one Cartesian state and converts its source km and km/s components to the simulator's SI units.
     *
     * @param object state JSON object
     * @param path   state path used in error reporting
     * @return SI state vector
     */
    private static StateVector parseStateVector(final JsonObject object, final String path)
    {
        Vector3D position = parseVector(requireArray(object, "position", path), path + ".position");
        Vector3D velocity = parseVector(requireArray(object, "velocity", path), path + ".velocity");
        return new StateVector(position.multiply(KILOMETRES_TO_METRES), velocity.multiply(KILOMETRES_TO_METRES));
    }

    /**
     * Parses exactly three source vector components.
     *
     * @param array vector component array
     * @param path  vector path used in error reporting
     * @return parsed vector in source units
     */
    private static Vector3D parseVector(final JsonArray array, final String path)
    {
        if (array.size() != 3)
        {
            throw new HorizonsDataFormatException(path + " must contain exactly three components");
        }
        double x = parseFiniteDouble(requireString(array, 0, path), path + "[0]");
        double y = parseFiniteDouble(requireString(array, 1, path), path + "[1]");
        double z = parseFiniteDouble(requireString(array, 2, path), path + "[2]");
        return new Vector3D(x, y, z);
    }

    /**
     * Requires an array-valued field.
     *
     * @param object containing object
     * @param field  required field name
     * @param path   containing path used in error reporting
     * @return required array
     */
    private static JsonArray requireArray(
            final JsonObject object,
            final String field,
            final String path) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonArray()) {
            throw new HorizonsDataFormatException(path + "." + field + " must be an array");
        }
        return element.getAsJsonArray();
    }

    /**
     * Requires an object-valued field.
     *
     * @param object containing object
     * @param field  required field name
     * @param path   containing path used in error reporting
     * @return required object
     */
    private static JsonObject requireObject(
            final JsonObject object,
            final String field,
            final String path) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonObject()) {
            throw new HorizonsDataFormatException(path + "." + field + " must be an object");
        }
        return element.getAsJsonObject();
    }

    /**
     * Requires a string element from an array.
     *
     * @param array vector array
     * @param index required index
     * @param path  containing path used in error reporting
     * @return required string
     */
    private static String requireString(final JsonArray array, final int index, final String path)
    {
        JsonElement element = array.get(index);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
        {
            throw new HorizonsDataFormatException(path + "[" + index + "] must be a string");
        }
        return element.getAsString();
    }

    /**
     * Requires a string-valued object field.
     *
     * @param object containing object
     * @param field  required field name
     * @param path   containing path used in error reporting
     * @return required string
     */
    private static String requireString(
            final JsonObject object,
            final String field,
            final String path) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new HorizonsDataFormatException(path + "." + field + " must be a string");
        }
        return element.getAsString();
    }

    /**
     * Enforces the frozen ADR-011 acquisition contract before the states are
     * admitted into simulation composition.
     *
     * @param provenance parsed source provenance
     */
    private static void validateProvenance(final HorizonsProvenance provenance)
    {
        requireExpected(provenance.apophisTarget(), EXPECTED_APOPHIS_TARGET,
                "apophisTarget");
        requireExpected(provenance.center(), EXPECTED_CENTER, "center");
        requireExpected(provenance.earthTarget(), EXPECTED_EARTH_TARGET, "earthTarget");
        requireExpected(provenance.outputType(), EXPECTED_OUTPUT_TYPE, "outputType");
        requireExpected(provenance.positionUnit(), EXPECTED_POSITION_UNIT, "positionUnit");
        requireExpected(provenance.referenceFrame(), EXPECTED_REFERENCE_FRAME,"referenceFrame");
        requireExpected(provenance.system(), EXPECTED_SYSTEM, "system");
        requireExpected(provenance.timeScale(), EXPECTED_TIME_SCALE, "timeScale");
        requireExpected(provenance.velocityUnit(), EXPECTED_VELOCITY_UNIT, "velocityUnit");
    }

    /**
     * Requires one provenance field to match the frozen acquisition contract.
     *
     * @param actual   fixture value
     * @param expected required value
     * @param field    field name used in error reporting
     */
    private static void requireExpected(final String actual, final String expected, final String field)
    {
        if (!expected.equals(actual)) {
            throw new HorizonsDataFormatException(
                    field + " must be '" + expected + "', was '" + actual + "'");
        }
    }// enf of requireExpected() -----------------------
}
