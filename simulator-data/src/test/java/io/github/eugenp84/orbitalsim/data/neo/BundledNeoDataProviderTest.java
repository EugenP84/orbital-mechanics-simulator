package io.github.eugenp84.orbitalsim.data.nasa;

import io.github.eugenp84.orbitalsim.data.neo.DataSource;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataResult;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BundledNeoDataProviderTest {

    private final BundledNeoDataProvider provider = new BundledNeoDataProvider();

    private static final LocalDate COVERED = LocalDate.of(2029, 4, 13);

    @Test
    void returnsCachedResultForTheCoveredWindow() {
        NeoDataResult result = provider.findCloseApproaches(COVERED, COVERED);

        assertEquals(DataSource.CACHED, result.source());
        assertTrue(result.isCached());
        assertEquals(9, result.objects().size());   // element_count in the capture
    }

    @Test
    void retrievedAtIsTheCaptureInstantNotLoadTime() {
        Instant before = Instant.now();
        NeoDataResult result = provider.findCloseApproaches(COVERED, COVERED);

        // The sidecar's capturedAt is in the past, not "now" — proving the
        // provider reports capture time, not load time (ADR-007 §7)
        assertTrue(result.retrievedAt().isBefore(before),
                "retrievedAt must be the original capture instant");
    }

    @Test
    void reusesTheSharedParserProducingNormalisedData() {
        NeoDataResult result = provider.findCloseApproaches(COVERED, COVERED);

        // Apophis, in SI units, exactly as the parser test asserts — proving the
        // bundled path and the live path share one parser (ADR-007 §7)
        var apophis = result.objects().stream()
                .filter(o -> o.id().equals("2099942"))
                .findFirst().orElseThrow();
        assertEquals(7422.5389568,
                apophis.closeApproaches().get(0).relativeVelocityMetresPerSecond(), 1e-6);
    }

    @Test
    void refusesAWindowTheSnapshotDoesNotCover() {
        LocalDate other = LocalDate.of(2025, 1, 1);
        assertThrows(NeoDataUnavailableException.class,
                () -> provider.findCloseApproaches(other, other));
    }

    @Test
    void refusesAPartialOverlapUnderTheExactMatchRule() {
        // A range that contains the covered day but is wider is NOT an exact
        // match, so it is refused — the conservative rule (ADR-007 §5, §7)
        assertThrows(NeoDataUnavailableException.class,
                () -> provider.findCloseApproaches(
                        LocalDate.of(2029, 4, 12), LocalDate.of(2029, 4, 14)));
    }

    @Test
    void validatesRequestedDates() {
        assertThrows(NullPointerException.class,
                () -> provider.findCloseApproaches(null, COVERED));
        assertThrows(NullPointerException.class,
                () -> provider.findCloseApproaches(COVERED, null));
        assertThrows(IllegalArgumentException.class,
                () -> provider.findCloseApproaches(
                        LocalDate.of(2029, 4, 14), LocalDate.of(2029, 4, 13)));
    }

    @Test
    void reportsAMissingBundledResourceAsUnavailable() {
        BundledNeoDataProvider missing = new BundledNeoDataProvider(
                "/neo/does-not-exist.json", "/neo/does-not-exist.meta.json",
                new NasaNeoWsParser());
        assertThrows(NeoDataUnavailableException.class,
                () -> missing.findCloseApproaches(COVERED, COVERED));
    }

    @Test
    void reportsAnUnparseableBundledDatasetAsUnavailable() {
        // A corrupt bundle whose window matches the request: compatibility
        // passes, so execution reaches the parser, which fails. This proves two
        // properties: the NASA-format failure is translated to provider-neutral
        // unavailability rather than leaked (ADR-007 §3, §5), and the original
        // failure is preserved as the cause so the diagnostic chain survives.
        //
        // It does NOT prove the catch is narrow: an unexpected RuntimeException
        // is not exercised here (the parser is constructed concretely, with no
        // seam to inject one), so this test cannot show that internal defects
        // surface. That narrowness is enforced by review and by ADR-007 §5, not
        // by this test.
        BundledNeoDataProvider corrupt = new BundledNeoDataProvider(
                "/neo/neows-feed-corrupt.json",
                "/neo/neows-feed-corrupt.meta.json",
                new NasaNeoWsParser());

        NeoDataUnavailableException thrown = assertThrows(
                NeoDataUnavailableException.class,
                () -> corrupt.findCloseApproaches(
                        LocalDate.of(2029, 4, 13), LocalDate.of(2029, 4, 13)));

        assertInstanceOf(NeoDataFormatException.class, thrown.getCause());
    }
}