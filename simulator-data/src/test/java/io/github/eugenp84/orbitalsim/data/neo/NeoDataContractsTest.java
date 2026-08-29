package io.github.eugenp84.orbitalsim.data.neo;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NeoDataContractsTest {

    private static CloseApproach sampleApproach() {
        return new CloseApproach(
                Instant.parse("2029-04-13T21:46:00Z"),
                3.8e7,      // ~38,000 km, an Apophis-scale close approach
                7.4e3,      // ~7.4 km/s
                "Earth");
    }

    private static NeoObjectData sampleObject() {
        return new NeoObjectData(
                "2099942", "99942 Apophis",
                3.1e2, 3.7e2, true, List.of(sampleApproach()));
    }

    // --- CloseApproach ---

    @Test
    void closeApproachRejectsInvalidValues() {
        Instant t = Instant.now();

        assertThrows(NullPointerException.class,
                () -> new CloseApproach(null, 1e7, 1e3, "Earth"));
        assertThrows(NullPointerException.class,
                () -> new CloseApproach(t, 1e7, 1e3, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CloseApproach(t, 1e7, 1e3, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> new CloseApproach(t, 0.0, 1e3, "Earth"));
        assertThrows(IllegalArgumentException.class,
                () -> new CloseApproach(t, -1.0, 1e3, "Earth"));
        assertThrows(IllegalArgumentException.class,
                () -> new CloseApproach(t, Double.NaN, 1e3, "Earth"));
        assertThrows(IllegalArgumentException.class,
                () -> new CloseApproach(t, 1e7, -1.0, "Earth"));
        assertThrows(IllegalArgumentException.class,
                () -> new CloseApproach(t, 1e7, Double.POSITIVE_INFINITY, "Earth"));
    }

    @Test
    void closeApproachAcceptsZeroRelativeVelocity() {
        // Zero relative velocity is physically degenerate but not invalid;
        // the contract forbids only negative or non-finite values
        assertDoesNotThrow(() -> new CloseApproach(Instant.now(), 1e7, 0.0, "Earth"));
    }

    // --- NeoObjectData ---

    @Test
    void neoObjectRejectsInvalidIdentityAndDiameters() {
        List<CloseApproach> approaches = List.of(sampleApproach());

        assertThrows(NullPointerException.class,
                () -> new NeoObjectData(null, "n", 1, 2, false, approaches));
        assertThrows(IllegalArgumentException.class,
                () -> new NeoObjectData(" ", "n", 1, 2, false, approaches));
        assertThrows(NullPointerException.class,
                () -> new NeoObjectData("id", null, 1, 2, false, approaches));
        assertThrows(IllegalArgumentException.class,
                () -> new NeoObjectData("id", " ", 1, 2, false, approaches));
        assertThrows(NullPointerException.class,
                () -> new NeoObjectData("id", "n", 1, 2, false, null));

        assertThrows(IllegalArgumentException.class,
                () -> new NeoObjectData("id", "n", 0.0, 2, false, approaches));
        assertThrows(IllegalArgumentException.class,
                () -> new NeoObjectData("id", "n", Double.NaN, 2, false, approaches));
        // max below min is rejected
        assertThrows(IllegalArgumentException.class,
                () -> new NeoObjectData("id", "n", 5.0, 4.0, false, approaches));
    }

    @Test
    void neoObjectAllowsEqualMinAndMaxDiameter() {
        // A point estimate (min == max) is legitimate
        assertDoesNotThrow(
                () -> new NeoObjectData("id", "n", 100.0, 100.0, false, List.of()));
    }

    @Test
    void neoObjectAllowsEmptyCloseApproaches() {
        NeoObjectData object = new NeoObjectData("id", "n", 1, 2, false, List.of());
        assertTrue(object.closeApproaches().isEmpty());
    }

    @Test
    void neoObjectDiameterMidpointIsTheAverage() {
        NeoObjectData object = new NeoObjectData("id", "n", 300.0, 380.0, false, List.of());
        assertEquals(340.0, object.estimatedDiameterMidpointMetres(), 1e-9);
    }

    @Test
    void neoObjectCloseApproachListIsImmutableAndDefensivelyCopied() {
        List<CloseApproach> mutable = new ArrayList<>();
        mutable.add(sampleApproach());

        NeoObjectData object = new NeoObjectData("id", "n", 1, 2, false, mutable);

        // Later mutation of the caller's list does not affect the object
        mutable.clear();
        assertEquals(1, object.closeApproaches().size());

        // The published list cannot be mutated
        assertThrows(UnsupportedOperationException.class,
                () -> object.closeApproaches().clear());
    }

    @Test
    void neoObjectRejectsNullCloseApproachElement() {
        List<CloseApproach> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(NullPointerException.class,
                () -> new NeoObjectData("id", "n", 1, 2, false, withNull));
    }

    // --- NeoDataResult ---

    @Test
    void resultRejectsNulls() {
        List<NeoObjectData> objects = List.of(sampleObject());
        Instant t = Instant.now();

        assertThrows(NullPointerException.class,
                () -> new NeoDataResult(null, DataSource.LIVE, t));
        assertThrows(NullPointerException.class,
                () -> new NeoDataResult(objects, null, t));
        assertThrows(NullPointerException.class,
                () -> new NeoDataResult(objects, DataSource.LIVE, null));
    }

    @Test
    void resultCarriesProvenanceExplicitly() {
        Instant captured = Instant.parse("2026-01-15T00:00:00Z");

        NeoDataResult live = new NeoDataResult(
                List.of(sampleObject()), DataSource.LIVE, captured);
        NeoDataResult cached = new NeoDataResult(
                List.of(sampleObject()), DataSource.CACHED, captured);

        assertEquals(DataSource.LIVE, live.source());
        assertFalse(live.isCached());

        assertEquals(DataSource.CACHED, cached.source());
        assertTrue(cached.isCached());

        // retrievedAt is the supplied capture time, not "now" — a cached
        // result therefore reports its true age (ADR-007 s7)
        assertEquals(captured, cached.retrievedAt());
    }

    @Test
    void resultObjectListIsImmutableAndDefensivelyCopied() {
        List<NeoObjectData> mutable = new ArrayList<>();
        mutable.add(sampleObject());

        NeoDataResult result = new NeoDataResult(mutable, DataSource.LIVE, Instant.now());

        mutable.clear();
        assertEquals(1, result.objects().size());

        assertThrows(UnsupportedOperationException.class,
                () -> result.objects().clear());
    }

    @Test
    void resultAllowsEmptyObjectList() {
        // A successful query that matched no NEOs is valid and distinct from
        // an acquisition failure
        NeoDataResult result = new NeoDataResult(List.of(), DataSource.LIVE, Instant.now());
        assertTrue(result.objects().isEmpty());
        assertEquals(DataSource.LIVE, result.source());
    }

    @Test
    void neoObjectDiameterMidpointRemainsFiniteForLargeFiniteBounds() {
        // Both bounds are finite and valid, but 0.5*(min+max) would overflow.
        // The robust formulation must stay finite and within [min, max].
        NeoObjectData object = new NeoObjectData(
                "id", "n", Double.MAX_VALUE / 2.0, Double.MAX_VALUE, false, List.of());

        double midpoint = object.estimatedDiameterMidpointMetres();
        assertTrue(Double.isFinite(midpoint));
        assertTrue(midpoint >= object.estimatedDiameterMinMetres());
        assertTrue(midpoint <= object.estimatedDiameterMaxMetres());
    }
}