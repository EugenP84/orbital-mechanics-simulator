package io.github.eugenp84.orbitalsim.learning.scenario;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioObservationCadenceTest
{

    @Test
    void rejectsANonPositiveOrNonFiniteTimestep() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScenarioObservationCadence(0.0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ScenarioObservationCadence(-1.0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ScenarioObservationCadence(Double.NaN, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ScenarioObservationCadence(Double.POSITIVE_INFINITY, 3));
    }

    @Test
    void rejectsANonPositiveStepCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScenarioObservationCadence(10.0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ScenarioObservationCadence(10.0, -1));
    }

    @Test
    void rejectsAnOverflowingInterval()
    {
        // Both inputs are individually valid, but their product is not representable, so no usable cadence exists and the object must not be constructible.
        assertThrows(IllegalArgumentException.class,
                () -> new ScenarioObservationCadence(1.0e308, 1000));
    }

    @Test
    void theIntervalIsDerivedFromTheTimestepAndStepCount()
    {
        ScenarioObservationCadence cadence = new ScenarioObservationCadence(10.0, 3);
        assertEquals(10.0, cadence.timeStepSeconds(), 1e-12);
        assertEquals(3, cadence.stepsPerObservation());
        assertEquals(30.0, cadence.intervalSeconds(), 1e-12);
    }

    @Test
    void aSubnormalIntervalIsPermittedBecauseItIsStillRepresentable()
    {
        // The record's contract is representability, not practicality. the session handles the numerical consequences of a tiny interval.
        ScenarioObservationCadence cadence =  new ScenarioObservationCadence(Double.MIN_VALUE, 1);

        assertEquals(Double.MIN_VALUE, cadence.intervalSeconds());
    }
}