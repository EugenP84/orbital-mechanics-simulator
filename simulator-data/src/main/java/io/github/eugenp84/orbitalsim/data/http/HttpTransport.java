package io.github.eugenp84.orbitalsim.data.http;

import java.net.URI;

/**
 * A minimal HTTP transport seam (ADR-007 §4). It knows only how to perform a GET against a URI
 * and return the completed response; it contains no knowledge of NeoWs, query parameters, JSON or fallback policy.
 * <p>
 * Isolating the one non-deterministic collaborator behind this interface is what lets the live provider's classification and fallback logic be tested
 * exhaustively and offline, with a fake transport supplying each response class.
 * The real network-backed implementation is a thin adapter added alongside the opt-in live integration test.
 */
public interface HttpTransport
{

    /**
     * Performs a GET request and returns the completed response.
     *
     * @param uri the absolute request URI
     * @return the status and body the server returned
     * @throws HttpTransportException if the request did not complete (timeout,
     *         connection failure, etc.) — no usable response was obtained
     */
    HttpResponseData get(URI uri);
}