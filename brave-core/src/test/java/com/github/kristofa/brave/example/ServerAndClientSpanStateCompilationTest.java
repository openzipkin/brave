package com.github.kristofa.brave.example;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerAndClientSpanState;
import com.github.kristofa.brave.ServerSpan;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import org.junit.Test;

/**
 * Example that shows ServerAndClientSpanState can be implemented outside brave's package.
 */
public class ServerAndClientSpanStateCompilationTest implements ServerAndClientSpanState {

    @Test
    public void buildsWithoutError() throws Exception {
        new Brave.Builder(this).build();
    }

    private Endpoint endpoint = new Endpoint(127 << 24 | 1, (short) 8080, "tomcat");
    private Span currentClientSpan = null;
    private ServerSpan currentServerSpan = ServerSpan.EMPTY;
    private String currentClientServiceName;

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
    public void setCurrentClientSpan(final Span span) {
        currentClientSpan = span;
    }

    @Override
    public void setCurrentClientServiceName(String serviceName) {
        currentClientServiceName = serviceName;
    }

    @Override
    public Boolean sample() {
        return currentServerSpan.getSample();
    }
}
