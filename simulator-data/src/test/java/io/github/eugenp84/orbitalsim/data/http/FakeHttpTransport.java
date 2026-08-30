package io.github.eugenp84.orbitalsim.data.http;

import java.net.URI;
import java.util.function.Supplier;

/** A test double for {@link HttpTransport}. Test-only; never production code. */
public final class FakeHttpTransport implements HttpTransport {

    private final Supplier<HttpResponseData> behaviour;
    private URI lastRequestedUri;
    private int callCount;

    private FakeHttpTransport(Supplier<HttpResponseData> behaviour) {
        this.behaviour = behaviour;
    }

    public static FakeHttpTransport responding(int status, String body) {
        return new FakeHttpTransport(() -> new HttpResponseData(status, body));
    }

    public static FakeHttpTransport failing(HttpTransportException failure) {
        return new FakeHttpTransport(() -> { throw failure; });
    }

    public URI lastRequestedUri() {
        return lastRequestedUri;
    }

    public int callCount() {
        return callCount;
    }

    @Override
    public HttpResponseData get(URI uri) {
        this.lastRequestedUri = uri;
        this.callCount++;
        return behaviour.get();
    }
}