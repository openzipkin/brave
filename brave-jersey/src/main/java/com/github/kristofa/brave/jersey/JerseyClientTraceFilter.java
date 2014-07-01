package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MultivaluedMap;

/**
 * This filter creates or forwards trace headers and sends cs and cr annotations.
 * Usage:
 * Client client = Client.create()
 * client.addFilter(new ClientTraceFilter(clientTracer));
 */
@Singleton
public class JerseyClientTraceFilter extends ClientFilter {

    private final ClientTracer clientTracer;

    @Inject
    public JerseyClientTraceFilter(ClientTracer clientTracer) {
        this.clientTracer = clientTracer;
    }

    @Override
    public ClientResponse handle(ClientRequest clientRequest) throws ClientHandlerException {
        String spanName = getSpanName(clientRequest);
        SpanId newSpan = clientTracer.startNewSpan(spanName);

        addTracingHeaders(clientRequest, newSpan);
        clientTracer.setClientSent();
        ClientResponse clientResponse = getNext().handle(clientRequest);
        clientTracer.submitBinaryAnnotation("http.responsecode", clientResponse.getStatus());
        clientTracer.setClientReceived();
        return clientResponse;
    }

    //Visible for testing
    void addTracingHeaders(ClientRequest clientRequest, SpanId newSpan) {
        MultivaluedMap<String, Object> headers = clientRequest.getHeaders();
        if (newSpan != null) {
            headers.add(BraveHttpHeaders.TraceId.getName(), String.valueOf(newSpan.getTraceId()));
            headers.add(BraveHttpHeaders.SpanId.getName(), String.valueOf(newSpan.getSpanId()));
            if(newSpan.getParentSpanId() != null) {
                headers.add(BraveHttpHeaders.ParentSpanId.getName(), String.valueOf(newSpan.getParentSpanId()));
            }
            headers.add(BraveHttpHeaders.Sampled.getName(), "true");
        } else {
            headers.add(BraveHttpHeaders.Sampled.getName(), "false");
        }
    }

    private String getSpanName(ClientRequest clientRequest) {
        String path = clientRequest.getURI().getPath();
        if (path != null) {
            return path;
        } else {
            return clientRequest.getURI().toString();
        }
    }
}
