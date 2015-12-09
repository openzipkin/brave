package com.github.kristofa.brave.example;

import com.github.kristofa.brave.ServerClientAndLocalSpanState;
import com.github.kristofa.brave.ServerSpan;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

/**
 * Example that shows ServerClientAndLocalSpanState can be implemented outside brave's package.
 */
public class TestServerClientAndLocalSpanStateCompilation implements ServerClientAndLocalSpanState {

    private Endpoint endpoint = new Endpoint(127 << 24 | 1, (short) 8080, "tomcat");
    private ServerSpan currentServerSpan = ServerSpan.EMPTY;
    private Span currentClientSpan = null;
    private String currentClientServiceName;
    private Span currentLocalSpan = null;

    @Override
    public ServerSpan getCurrentServerSpan() {
        return currentServerSpan;
    }

    @Override
    public void setCurrentServerSpan(final ServerSpan span) {
        currentServerSpan = span;
    }

    @Override
    public Endpoint getServerEndpoint() {
        return endpoint;
    }

    @Override
    public Span getCurrentClientSpan() {
        return currentClientSpan;
    }

    @Override
    public Endpoint getClientEndpoint() {
        if (currentClientServiceName == null) {
            return endpoint;
        } else {
            return new Endpoint(endpoint).setService_name(currentClientServiceName);
        }
    }

    @Override
    public void setCurrentClientSpan(Span span) {
        currentClientSpan = span;
    }

    @Override
    public void setCurrentClientServiceName(String serviceName) {
        currentClientServiceName = serviceName;
    }

    @Override
    public Boolean sample() {
        return currentServerSpan == null ? null : currentServerSpan.getSample();
    }

    @Override
    public Span getCurrentLocalSpan() {
        return currentLocalSpan;
    }

    @Override
    public void setCurrentLocalSpan(Span span) {
        currentLocalSpan = span;
    }
}
