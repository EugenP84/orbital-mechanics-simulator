package io.github.eugenp84.orbitalsim.data.http;

import java.net.URI;

/**
 * A minimal HTTP transport seam (ADR-007 §4). It knows only how to perform a GET against a URI
 * and return the completed response; it contains no knowledge of NeoWs, query parameters, JSON or fallback policy.
 * <p>
 * @author Eugeniu PLATITA
 */
public interface HttpTransport
{

    /**
     * Performs a GET request and returns the completed response.
     *
     * @param uri the absolute request URI
     * @return the status and body the server returned
     * @throws HttpTransportException if the request did not complete (timeout,  connection failure, etc.) — no usable response was obtained
     */
    HttpResponseData get(URI uri);
}