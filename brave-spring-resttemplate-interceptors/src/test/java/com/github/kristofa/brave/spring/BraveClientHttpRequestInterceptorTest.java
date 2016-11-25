package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import zipkin.TraceKeys;

import java.io.IOException;
import java.net.URI;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
public class BraveClientHttpRequestInterceptorTest {
    @Autowired ClientTracer clientTracer;
    @Autowired SpanNameProvider spanNameProvider;
    @Autowired BraveClientHttpRequestInterceptor subject;

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
        when(clientTracer.startNewSpan(spanName)).thenReturn(
            SpanId.builder().traceId(1L).spanId(1L).parentId(1L).build());

        try {
            subject.intercept(request, body, execution);
        } finally {
            final InOrder order = inOrder(clientTracer, execution);

            order.verify(clientTracer).startNewSpan(spanName);
            order.verify(clientTracer).submitBinaryAnnotation(TraceKeys.HTTP_URL, url);
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
        when(clientTracer.startNewSpan(spanName)).thenReturn(
            SpanId.builder().traceId(1L).spanId(1L).parentId(1L).build());

        subject.intercept(request, body, execution);

        final InOrder order = inOrder(clientTracer, execution);

        order.verify(clientTracer).startNewSpan(spanName);
        order.verify(clientTracer).submitBinaryAnnotation(TraceKeys.HTTP_URL, url);
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
        when(clientTracer.startNewSpan(spanName)).thenReturn(
            SpanId.builder().traceId(1L).spanId(1L).parentId(1L).build());

        final ClientHttpResponse actual = subject.intercept(request, body, execution);

        assertSame(expected, actual);

        final InOrder order = inOrder(clientTracer, execution);

        order.verify(clientTracer).startNewSpan(spanName);
        order.verify(clientTracer).submitBinaryAnnotation(TraceKeys.HTTP_URL, url);
        order.verify(clientTracer).setClientSent();
        order.verify(execution).execute(request, body);
        order.verify(clientTracer).submitBinaryAnnotation(TraceKeys.HTTP_STATUS_CODE, String.valueOf(status.value()));
        order.verify(clientTracer).setClientReceived();
    }

    @Configuration
    @Import(BraveClientHttpRequestInterceptor.class)
    static class AppConfig {
        @Bean ClientTracer clientTracer() {
            return mock(ClientTracer.class);
        }

        @Bean Brave brave(ClientTracer clientTracer) {
            Brave brave = mock(Brave.class);
            when(brave.clientRequestInterceptor())
                    .thenReturn(new ClientRequestInterceptor(clientTracer));
            when(brave.clientResponseInterceptor())
                    .thenReturn(new ClientResponseInterceptor(clientTracer));
            return brave;
        }

        @Bean SpanNameProvider spanNameProvider() {
            return mock(SpanNameProvider.class);
        }
    }
}
