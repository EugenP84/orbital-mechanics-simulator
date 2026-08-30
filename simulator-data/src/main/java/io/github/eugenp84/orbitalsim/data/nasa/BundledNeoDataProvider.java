package io.github.eugenp84.orbitalsim.data.nasa;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import io.github.eugenp84.orbitalsim.data.neo.DataSource;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataProvider;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataResult;
import io.github.eugenp84.orbitalsim.data.neo.NeoDataUnavailableException;
import io.github.eugenp84.orbitalsim.data.neo.NeoObjectData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * A {@link NeoDataProvider} backed by an immutable NEO dataset bundled into the
 * application as a classpath resource, with a machine-readable metadata sidecar
 * recording its capture time and query window (ADR-007 §7).
 * <p>
 * This is a <strong>bundled snapshot, not a runtime cache</strong>: it is never populated from live calls, never evicted, never refreshed.
 * It provides the deterministic offline dataset that keeps the tool usable without a network,
 * and serves as the fallback source for the live provider.
 * <p>
 * It reads the bundled JSON through the same {@link NasaNeoWsParser} used for
 * live responses (the single parsing path of ADR-007 §7), and stamps its
 * results with {@link DataSource#CACHED} and the <em>original capture time</em>
 * from the sidecar — never the load time — so a stale snapshot reports its true
 * age (ADR-007 §7).
 * <p>
 * <strong>Query compatibility (ADR-007 §5, §7).</strong> The bundled dataset
 * satisfies a request only when the requested date range exactly matches the captured window.
 * Exact match is deliberately stricter than range containment:
 * the current provider-neutral model does not retain per-object feed-bucket
 * dates, so a subset of a wider capture cannot yet be filtered correctly, and
 * refusing is safer than over-returning. The rule can be relaxed to containment
 * once date-window filtering exists. A request the snapshot does not cover
 * yields {@link NeoDataUnavailableException}, never unrelated data.
 */
public final class BundledNeoDataProvider implements NeoDataProvider {

    private final String datasetResourcePath;
    private final String metadataResourcePath;
    private final NasaNeoWsParser parser;
    private final Gson gson = new Gson();

    /** The default bundled dataset: the 2029-04-13 Apophis-window capture. */
    public BundledNeoDataProvider()
    {
        this("/neo/neows-feed-2029-04-13.json",
                "/neo/neows-feed-2029-04-13.meta.json",
                new NasaNeoWsParser());
    }

    /**
     * @param datasetResourcePath  classpath path to the bundled feed JSON
     * @param metadataResourcePath classpath path to the sidecar metadata
     * @param parser               the shared NeoWs parser
     */
    public BundledNeoDataProvider(
            String datasetResourcePath, String metadataResourcePath, NasaNeoWsParser parser) {
        this.datasetResourcePath =
                Objects.requireNonNull(datasetResourcePath, "datasetResourcePath must not be null");
        this.metadataResourcePath =
                Objects.requireNonNull(metadataResourcePath, "metadataResourcePath must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
    }

    @Override
    public NeoDataResult findCloseApproaches(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "endDate (" + endDate + ") must not be before startDate (" + startDate + ")");
        }

        BundledMetadata metadata = loadMetadata();

        // Exact-match compatibility (see class Javadoc). Not covered -> the
        // snapshot does not satisfy this request; unavailable, never wrong data.
        if (!startDate.equals(metadata.start) || !endDate.equals(metadata.end)) {
            throw new NeoDataUnavailableException(
                    "bundled dataset covers " + metadata.start + ".." + metadata.end
                            + " and does not cover requested " + startDate + ".." + endDate);
        }

        String json = loadResource(datasetResourcePath);

        final List<NeoObjectData> objects;
        try {
            objects = parser.parseFeed(json);
        } catch (NeoDataFormatException e) {
            // Translate the NASA-representation failure into the provider-neutral
            // "unavailable" at this boundary: a corrupt bundle means this
            // provider cannot supply the request, handled identically to a
            // missing resource or incompatible window. The NASA-specific format
            // type must not escape the provider-neutral contract, and the live
            // provider's fallback path can then treat all "fallback unusable"
            // cases uniformly (ADR-007 §3, §5). Only the expected format failure
            // is caught; an unexpected internal defect surfaces (ADR-007 §5).
            throw new NeoDataUnavailableException(
                    "bundled dataset could not be parsed: " + datasetResourcePath, e);
        }

        return new NeoDataResult(objects, DataSource.CACHED, metadata.capturedAt);
    }

    private BundledMetadata loadMetadata() {
        String raw = loadResource(metadataResourcePath);

        MetadataDto dto;
        try {
            dto = gson.fromJson(raw, MetadataDto.class);
        } catch (JsonSyntaxException e) {
            throw new NeoDataUnavailableException(
                    "bundled metadata was not valid JSON: " + metadataResourcePath, e);
        }
        if (dto == null || dto.startDate == null || dto.endDate == null
                || dto.capturedAt == null) {
            throw new NeoDataUnavailableException(
                    "bundled metadata missing required fields: " + metadataResourcePath);
        }

        try {
            return new BundledMetadata(
                    LocalDate.parse(dto.startDate),
                    LocalDate.parse(dto.endDate),
                    Instant.parse(dto.capturedAt));
        } catch (DateTimeParseException e) {
            throw new NeoDataUnavailableException(
                    "bundled metadata had an unparseable date/instant: " + metadataResourcePath, e);
        }
    }

    private String loadResource(String path) {
        try (InputStream in = BundledNeoDataProvider.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new NeoDataUnavailableException("bundled resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NeoDataUnavailableException("failed to read bundled resource: " + path, e);
        }
    }

    /** Parsed, validated sidecar metadata. */
    private record BundledMetadata(LocalDate start, LocalDate end, Instant capturedAt) { }

    /** Gson shape of the sidecar; private to this provider. */
    private static final class MetadataDto {
        @SerializedName("startDate")
        String startDate;
        @SerializedName("endDate")
        String endDate;
        @SerializedName("capturedAt")
        String capturedAt;
        @SerializedName("source")
        String source;
    }
}