package com.github.kristofa.brave;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Random;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ServletHandlerInterceptorTest {

    private static final Random RAND = new Random();
    private ServletHandlerInterceptor subject;
    private ServerTracer serverTracer;
    private EndpointSubmitter submitter;
    private ServerSpanThreadBinder serverThreadBinder;

    @Before
    public void setUp() throws Exception {
        serverTracer = mock(ServerTracer.class);
        submitter = mock(EndpointSubmitter.class);
        serverThreadBinder = mock(ServerSpanThreadBinder.class);
        subject = new ServletHandlerInterceptor(serverTracer, serverThreadBinder, submitter);
    }

    @Test
    public void afterCompletionShouldNotifyOfCompletionAndTidyUp() {
        subject.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), this, null);

        final InOrder order = inOrder(serverTracer);

        order.verify(serverTracer).setServerSend();
        order.verify(serverTracer).clearCurrentSpan();
    }

    @Test
    public void afterCompletionShouldResetServerTraceOnAsyncCalls() {
        final ServerSpan span = mock(ServerSpan.class);

        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ServletHandlerInterceptor.HTTP_SERVER_SPAN_ATTRIBUTE, span);

        subject.afterCompletion(request, new MockHttpServletResponse(), this, null);

        final InOrder order = inOrder(serverThreadBinder, serverTracer);

        order.verify(serverThreadBinder).setCurrentSpan(same(span));
        order.verify(serverTracer).setServerSend();
        order.verify(serverTracer).clearCurrentSpan();
    }

    @Test
    public void preHandleShouldNotAssumeParentIdIsPresent() {
        final MockHttpServletRequest request = new MockHttpServletRequest();

        when(submitter.endpointSubmitted()).thenReturn(true);

        final String name = randomAlphanumeric(20);
        final long spanId = RAND.nextLong();
        final long traceId = RAND.nextLong();

        request.setRequestURI(name);
        request.addHeader(BraveHttpHeaders.SpanId.getName(), Long.toHexString(spanId));
        request.addHeader(BraveHttpHeaders.TraceId.getName(), Long.toHexString(traceId));

        subject.preHandle(request, new MockHttpServletResponse(), this);

        final InOrder order = inOrder(serverTracer);
        order.verify(serverTracer).setStateCurrentTrace(traceId, spanId, null, name);
        order.verify(serverTracer).setServerReceived();
        order.verifyNoMoreInteractions();

        verify(submitter).endpointSubmitted();
        verifyNoMoreInteractions(submitter);
    }

    @Test
    public void preHandleShouldHonourSampledFlag() {
        final MockHttpServletRequest request = new MockHttpServletRequest();

        when(submitter.endpointSubmitted()).thenReturn(true);

        request.addHeader(BraveHttpHeaders.Sampled.getName(), false);

        subject.preHandle(request, new MockHttpServletResponse(), this);

        verify(submitter).endpointSubmitted();
        verifyNoMoreInteractions(submitter);
        verify(serverTracer).setStateNoTracing();
    }

    @Test
    public void preHandleShouldSubmitEndpointIfNotSubmitted() {
        final String address = randomAlphanumeric(20);
        final int port = RAND.nextInt();
        final String serviceName = randomAlphanumeric(20);

        final MockHttpServletRequest request = new MockHttpServletRequest();

        when(submitter.endpointSubmitted()).thenReturn(false);

        request.addHeader(BraveHttpHeaders.Sampled.getName(), false);
        request.setLocalAddr(address);
        request.setLocalPort(port);
        request.setContextPath(serviceName);

        subject.preHandle(request, new MockHttpServletResponse(), this);

        verify(submitter).endpointSubmitted();
        verify(submitter).submit(eq(address), eq(port), eq(serviceName));
        verifyNoMoreInteractions(submitter);
        verify(serverTracer).setStateNoTracing();
        verifyNoMoreInteractions(serverTracer);
    }

    @Test
    public void preHandleShouldSetUnknownStateIfSpanIdMissing() {
        final MockHttpServletRequest request = new MockHttpServletRequest();

        when(submitter.endpointSubmitted()).thenReturn(true);

        final String name = randomAlphanumeric(20);

        request.setRequestURI(name);

        subject.preHandle(request, new MockHttpServletResponse(), this);

        final InOrder order = inOrder(serverTracer);
        order.verify(serverTracer).setStateUnknown(eq(name));
        order.verify(serverTracer).setServerReceived();
        order.verifyNoMoreInteractions();

        verify(submitter).endpointSubmitted();
        verifyNoMoreInteractions(submitter);
    }

    @Test
    public void preHandleShouldSetCurrentTraceIfSpanAndTraceAreKnown() {
        final MockHttpServletRequest request = new MockHttpServletRequest();

        when(submitter.endpointSubmitted()).thenReturn(true);

        final String name = randomAlphanumeric(20);
        final long spanId = RAND.nextLong();
        final long traceId = RAND.nextLong();
        final long parentSpanId = RAND.nextLong();

        request.setRequestURI(name);
        request.addHeader(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(spanId));
        request.addHeader(BraveHttpHeaders.TraceId.getName(), IdConversion.convertToString(traceId));
        request.addHeader(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(parentSpanId));

        subject.preHandle(request, new MockHttpServletResponse(), this);

        final InOrder order = inOrder(serverTracer);
        order.verify(serverTracer).setStateCurrentTrace(eq(traceId), eq(spanId), eq(parentSpanId), eq(name));
        order.verify(serverTracer).setServerReceived();
        order.verifyNoMoreInteractions();

        verify(submitter).endpointSubmitted();
        verifyNoMoreInteractions(submitter);
    }

    @Test
    public void preHandleShouldSupportNegativeTraceId() {
        final MockHttpServletRequest request = new MockHttpServletRequest();

        when(submitter.endpointSubmitted()).thenReturn(true);

        final String name = randomAlphanumeric(20);
        final long spanId = 123L;
        final long traceId = -4667777584646200191L;
        final long parentSpanId = 789L;

        request.setRequestURI(name);
        request.addHeader(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(spanId));
        request.addHeader(BraveHttpHeaders.TraceId.getName(), IdConversion.convertToString(traceId));
        request.addHeader(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(parentSpanId));

        subject.preHandle(request, new MockHttpServletResponse(), this);

        verify(serverTracer).setStateCurrentTrace(eq(traceId), eq(spanId), eq(parentSpanId), eq(name));
    }

    @Test
    public void preHandleShouldSupportNegativeSpanId() {
        final MockHttpServletRequest request = new MockHttpServletRequest();

        when(submitter.endpointSubmitted()).thenReturn(true);

        final String name = randomAlphanumeric(20);
        final long spanId = -123L;
        final long traceId = 456L;
        final long parentSpanId = 789L;

        request.setRequestURI(name);
        request.addHeader(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(spanId));
        request.addHeader(BraveHttpHeaders.TraceId.getName(), IdConversion.convertToString(traceId));
        request.addHeader(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(parentSpanId));

        subject.preHandle(request, new MockHttpServletResponse(), this);

        verify(serverTracer).setStateCurrentTrace(eq(traceId), eq(spanId), eq(parentSpanId), eq(name));
    }

    @Test
    public void preHandleShouldSupportNegativeParentSpanId() {
        final MockHttpServletRequest request = new MockHttpServletRequest();

        when(submitter.endpointSubmitted()).thenReturn(true);

        final String name = randomAlphanumeric(20);
        final long spanId = 123L;
        final long traceId = 456L;
        final long parentSpanId = -789L;

        request.setRequestURI(name);
        request.addHeader(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(spanId));
        request.addHeader(BraveHttpHeaders.TraceId.getName(), IdConversion.convertToString(traceId));
        request.addHeader(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(parentSpanId));

        subject.preHandle(request, new MockHttpServletResponse(), this);

        verify(serverTracer).setStateCurrentTrace(eq(traceId), eq(spanId), eq(parentSpanId), eq(name));
    }


    @Test
    public void afterConcurrentHandlingStartedShouldSaveStateAndClear() {

        final ServerSpan serverSpan = mock(ServerSpan.class);

        when(serverThreadBinder.getCurrentServerSpan()).thenReturn(serverSpan);

        final MockHttpServletRequest request = new MockHttpServletRequest();
        subject.afterConcurrentHandlingStarted(request, new MockHttpServletResponse(), this);

        assertSame(serverSpan, request.getAttribute(ServletHandlerInterceptor.HTTP_SERVER_SPAN_ATTRIBUTE));

        verify(serverTracer).clearCurrentSpan();
    }
}