package io.github.eugenp84.orbitalsim.data.http;

/**
 * A completed HTTP response: the status the server returned and the body it sent.
 * Deliberately HTTP-generic. It carries no NASA or NeoWs semantics.
 *
 * @param statusCode the HTTP status code
 * @param body       the response body; may be empty but never null
 *
 * @author Eugeniu PLATITA
 */
public record HttpResponseData(int statusCode, String body)
{

    public HttpResponseData
    {
        if (body == null) {
            throw new IllegalArgumentException("body must not be null (use empty string)");
        }// end if
    }
}