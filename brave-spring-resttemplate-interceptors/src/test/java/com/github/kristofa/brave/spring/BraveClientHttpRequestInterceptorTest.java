package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BraveClientHttpRequestInterceptorTest {

    private final ClientTracer clientTracer = mock(ClientTracer.class);
    private final SpanNameProvider spanNameProvider = mock(SpanNameProvider.class);
    private final BraveClientHttpRequestInterceptor subject = new BraveClientHttpRequestInterceptor(new ClientRequestInterceptor(clientTracer),
            new ClientResponseInterceptor(clientTracer), spanNameProvider);

    @Test(expected = IOException.class)
    public void interceptShouldLetExceptionOccurringDuringExecuteBlowUp() throws Exception {
        final String url = "http://example.com";
        final HttpMethod method = HttpMethod.HEAD;

        final MockClientHttpRequest request = new MockClientHttpRequest(method, URI.create(url));
        final byte[] body = new byte[12];
        final String spanName = randomAlphanumeric(20);

        final ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);

        when(execution.execute(request, body)).thenThrow(new IOException());
        when(spanNameProvider.spanName(any())).thenReturn(spanName);
        when(clientTracer.startNewSpan(spanName)).thenReturn(SpanId.create(1, 1, 1L));

        try {
            subject.intercept(request, body, execution);
        } finally {
            final InOrder order = inOrder(clientTracer, execution);

            order.verify(clientTracer).startNewSpan(spanName);
            order.verify(clientTracer).submitBinaryAnnotation("http.uri", url);
            order.verify(clientTracer).setClientSent();
            order.verify(execution).execute(request, body);
            order.verify(clientTracer).setClientReceived();
        }
    }

    @Test
    public void interceptShouldNotBlowUpIfExceptionOccursWhenGettingStatusCode() throws IOException {
        final String url = "http://example.com";
        final HttpMethod method = HttpMethod.HEAD;

        final MockClientHttpRequest request = new MockClientHttpRequest(method, URI.create(url));
        final byte[] body = new byte[12];
        final String spanName = randomAlphanumeric(20);

        final ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);

        final ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(response.getRawStatusCode()).thenThrow(new IOException());
        when(execution.execute(request, body)).thenReturn(response);
        when(spanNameProvider.spanName(any())).thenReturn(spanName);
        when(clientTracer.startNewSpan(spanName)).thenReturn(SpanId.create(1, 1, 1L));

        subject.intercept(request, body, execution);

        final InOrder order = inOrder(clientTracer, execution);

        order.verify(clientTracer).startNewSpan(spanName);
        order.verify(clientTracer).submitBinaryAnnotation("http.uri", url);
        order.verify(clientTracer).setClientSent();
        order.verify(execution).execute(request, body);
        order.verify(clientTracer).setClientReceived();
    }

    @Test
    public void interceptShouldReportResponseStatus() throws IOException {

        final String url = "http://example.com";
        final HttpMethod method = HttpMethod.HEAD;
        final HttpStatus status = HttpStatus.BANDWIDTH_LIMIT_EXCEEDED;

        final MockClientHttpRequest request = new MockClientHttpRequest(method, URI.create(url));
        final byte[] body = new byte[12];
        final String spanName = randomAlphanumeric(20);
        final MockClientHttpResponse expected = new MockClientHttpResponse(new byte[24], status);

        final ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);

        when(execution.execute(request, body)).thenReturn(expected);
        when(spanNameProvider.spanName(any())).thenReturn(spanName);
        when(clientTracer.startNewSpan(spanName)).thenReturn(SpanId.create(1, 1, 1L));

        final ClientHttpResponse actual = subject.intercept(request, body, execution);

        assertSame(expected, actual);

        final InOrder order = inOrder(clientTracer, execution);

        order.verify(clientTracer).startNewSpan(spanName);
        order.verify(clientTracer).submitBinaryAnnotation("http.uri", url);
        order.verify(clientTracer).setClientSent();
        order.verify(execution).execute(request, body);
        order.verify(clientTracer).submitBinaryAnnotation("http.responsecode", String.valueOf(status.value()));
        order.verify(clientTracer).setClientReceived();
    }


}
