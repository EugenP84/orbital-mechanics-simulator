package io.github.eugenp84.orbitalsim.data.nasa;

import io.github.eugenp84.orbitalsim.data.http.JdkHttpTransport;
import io.github.eugenp84.orbitalsim.data.neo.DataSource;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataProvider;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataResult;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataUnavailableException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in live integration test against NASA NeoWs. Tagged {@code "live"} and excluded from the ordinary test suite; run deliberately via the {@code liveTest} Gradle task.
 * <p>
 * It reads the real key from {@code NASA_API_KEY} and skips (does not fail) when the key is absent — a missing key means "not configured to run,"
 * not "integration broken." It never falls back to {@code DEMO_KEY}.
 * <p>
 * The test's sole purpose is to prove that the real {@link JdkHttpTransport}, the real NASA endpoint, and the configured key work together.
 * It therefore requires {@link DataSource#LIVE} and is given a fallback that throws if consulted: accepting CACHED would let the test pass when DNS
 * is broken, NASA is unreachable, the adapter is defective, or NASA returns 429/5xx — defeating its purpose.
 * Fallback behaviour is proved exhaustively by the offline suite.
 * <p>
 * The test never logs the request URI or the key (the URI carries the key).
 */
@Tag("live")
class NasaNeoLiveIntegrationTest
{

    @Test
    void fetchesRealNeoWsDataForTheApophisWindow()
    {
        String apiKey = System.getenv("NASA_API_KEY");
        Assumptions.assumeTrue(
                apiKey != null && !apiKey.isBlank(),
                "NASA_API_KEY must be set to run the live integration test");

        // A fallback that throws if consulted, so any accidental fallback fails loudly rather than turning the test green. =================================
        NeoDataProvider noFallback = (start, end) -> {
            throw new NeoDataUnavailableException(
                    "live integration test must not use fallback");
        };

        NasaNeoDataProvider provider = new NasaNeoDataProvider(
                new JdkHttpTransport(), new NasaNeoWsParser(), noFallback, apiKey);

        // A fixed close-approach window. 2029-04-13 is a future predicted close-approach date (Apophis);
        // NeoWs serves predicted approaches, so the window is stable for the test without being time-sensitive.
        LocalDate day = LocalDate.of(2029, 4, 13);
        NeoDataResult result = provider.findCloseApproaches(day, day);

        assertEquals(DataSource.LIVE, result.source(),"live integration test must obtain data from NASA, not fallback");
        assertNotNull(result.retrievedAt());
        // NeoDataResult already guarantees a non-null list, so assert something meaningful:
        // this fixed window should yield at least one close approach, proving real NeoWs data was obtained.
        // Not an exact count that would couple the test to a mutable external dataset. !!!!!!!
        assertFalse(result.objects().isEmpty(),"the selected NeoWs window should contain at least one close approach");
    }
}