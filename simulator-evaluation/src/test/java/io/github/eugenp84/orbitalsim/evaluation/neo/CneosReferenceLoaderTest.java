package io.github.eugenp84.orbitalsim.evaluation.neo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests preservation and SI normalisation of the JPL CAD Apophis reference.
 *
 * @author Eugeniu PLATITA
 */
public final class CneosReferenceLoaderTest
{

    /**
     * Verifies the identifying provenance and authoritative source values.
     */
    @Test
    public void loadsExpectedApophisReference()
    {
        CneosCloseApproachReference reference = new CneosReferenceLoader().load();
        assertEquals("NASA/JPL SBDB Close Approach Data API", reference.source());
        assertEquals("1.5", reference.apiVersion());
        assertEquals("99942", reference.designation());
        assertEquals("206", reference.orbitId());
        assertEquals("Earth", reference.approachBody());
        assertEquals(2_462_240.407091595, reference.epochJdTdb(), 0.0);
        assertEquals(7_422.49308586014, reference.relativeSpeedMetresPerSecond(), 1.0e-9);
    }

    /**
     * Verifies exact-au conversion and ordering of the published 3-sigma interval.
     */
    @Test
    public void normalisesDistanceToMetresAndPreservesUncertaintyOrdering()
    {
        CneosCloseApproachReference reference = new CneosReferenceLoader().load();
        double expectedMetres = 0.000254099098170977 * 149_597_870_700.0;

        assertEquals(expectedMetres, reference.nominalDistanceMetres(), 1.0e-6);
        assertTrue(reference.minimum3SigmaDistanceMetres()
                < reference.nominalDistanceMetres());
        assertTrue(reference.nominalDistanceMetres()
                < reference.maximum3SigmaDistanceMetres());
    }
}
