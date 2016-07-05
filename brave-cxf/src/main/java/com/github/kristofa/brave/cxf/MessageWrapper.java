package com.github.kristofa.brave.cxf;

import org.apache.cxf.message.Message;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Michał Podsiedzik
 */
public class MessageWrapper {
    protected final Message message;

    public MessageWrapper(final Message message) {
        this.message = message;
    }

    protected URI getUri() {
        try {
            return new URI((String) message.getExchange().get(Message.ENDPOINT_ADDRESS));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    protected void addHeader(final String header, final String value) {
        getHeaders(message).put(header, Arrays.asList(value));
    }

    protected String getHeader(final String header) {
        List<String> values = getHeaders(message).get(header);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }

    protected Map<String, List<String>> getHeaders(final Message message) {
        Map<String, List<String>> headers = (Map<String, List<String>>) message.get(Message.PROTOCOL_HEADERS);
        if (headers == null) {
            headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            message.put(Message.PROTOCOL_HEADERS, headers);
        }
        return headers;
    }

    protected String getHttpMethod() {
        return (String) message.get(Message.HTTP_REQUEST_METHOD);
    }

    protected int getHttpResponseCode() {
        Integer code = (Integer) message.get(Message.RESPONSE_CODE);
        if (code != null) {
            return code.intValue();
        } else {
            // Correct assumption?
            return 200;
        }
    }
}