package io.github.eugenp84.orbitalsim.data.horizons;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the canonical Horizons fixture contract and its SI normalisation.
 *
 * @author Eugeniu PLATITA
 */
class HorizonsExperimentLoaderTest
{

    private static final double AU_METRES = 149_597_870_700.0;
    private static final double DISTANCE_TOLERANCE_METRES = 1.0;
    private static final double SPEED_TOLERANCE_METRES_PER_SECOND = 1.0e-6;

    /**
     * Confirms that the three authoritative candidate epochs are loaded exactly.
     */
    @Test
    void loadsCandidateEpochs()
    {
        HorizonsExperimentData data = new HorizonsExperimentLoader().load();
        List<HorizonsEpochState> states = data.states();

        assertEquals(3, states.size());
        assertEquals(2462210.407091595, states.get(0).jdTdb(), 0.0);
        assertEquals(2462233.407091595, states.get(1).jdTdb(), 0.0);
        assertEquals(2462239.407091595, states.get(2).jdTdb(), 0.0);
    }

    /**
     * Confirms that source provenance matches the controlled Horizons acquisition.
     */
    @Test
    void loadsVerifiedProvenance() {
        HorizonsProvenance provenance = new HorizonsExperimentLoader().load().provenance();
        assertEquals("NASA/JPL Horizons", provenance.system());
        assertEquals("Earth (399)", provenance.earthTarget());
        assertEquals("99942 Apophis (2004 MN4)", provenance.apophisTarget());
        assertEquals("JPL#220", provenance.apophisSolution());
        assertEquals("Sun (10)", provenance.center());
        assertEquals("Ecliptic of J2000.0", provenance.referenceFrame());
        assertEquals("TDB", provenance.timeScale());
        assertEquals("GEOMETRIC cartesian states", provenance.outputType());
        assertEquals("km", provenance.positionUnit());
        assertEquals("km/s", provenance.velocityUnit());
    }

    /**
     * Confirms that Earth-to-Apophis separation decreases across the three deliberately ordered pre-encounter candidate epochs.
     */
    @Test
    void preservesApproachGeometryAcrossCandidates() {
        List<HorizonsEpochState> states = new HorizonsExperimentLoader().load().states();

        double distanceThirtyDays = separation(states.get(0));
        double distanceSevenDays = separation(states.get(1));
        double distanceOneDay = separation(states.get(2));
        assertTrue(distanceThirtyDays > distanceSevenDays);
        assertTrue(distanceSevenDays > distanceOneDay);
    }

    /**
     * Confirms exact source-to-SI conversion for representative Earth and Apophis components from the one-day candidate.
     */
    @Test
    void normalisesSourceVectorsToSi() {
        HorizonsEpochState state = new HorizonsExperimentLoader().load().states().get(2);

        assertEquals(-1.382162456064592E+11, state.earthState().position().x(), DISTANCE_TOLERANCE_METRES);
        assertEquals(-2.757278429027363E+04, state.earthState().velocity().y(), SPEED_TOLERANCE_METRES_PER_SECOND);
        assertEquals(-1.386256313085135E+11, state.apophisState().position().x(), DISTANCE_TOLERANCE_METRES);
        assertEquals(1.664942291305919E+03, state.apophisState().velocity().z(), SPEED_TOLERANCE_METRES_PER_SECOND);
    }

    /**
     * Confirms the broad physical sanity checks without using them as substitutes for provenance/frame validation.
     */
    @Test
    void passesEarthPhysicalSanityChecks()
    {
        List<HorizonsEpochState> states = new HorizonsExperimentLoader().load().states();

        for (HorizonsEpochState state : states)
        {
            double distanceAu = state.earthState().position().magnitude() / AU_METRES;
            double speedKilometresPerSecond = state.earthState().velocity().magnitude() / 1000.0;
            assertTrue(distanceAu > 0.98 && distanceAu < 1.02);
            assertTrue(speedKilometresPerSecond > 29.0 && speedKilometresPerSecond < 31.0);
        }
    }

    /**
     * Computes Earth-relative separation for one paired state.
     *
     * @param state paired Earth/Apophis state
     * @return separation in metres
     */
    private static double separation(final HorizonsEpochState state) {
        StateVector earth = state.earthState();
        StateVector apophis = state.apophisState();
        return earth.position().distanceTo(apophis.position());
    }
}
