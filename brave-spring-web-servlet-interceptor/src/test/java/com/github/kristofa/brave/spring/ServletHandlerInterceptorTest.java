package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseAdapter;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServletHandlerInterceptorTest {

    private ServletHandlerInterceptor subject;
    private ServerSpanThreadBinder serverThreadBinder;
    private ServerRequestInterceptor requestInterceptor;
    private SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();
    private ServerResponseInterceptor responseInterceptor;

    @Before
    public void setUp() throws Exception {
        requestInterceptor = mock(ServerRequestInterceptor.class);
        responseInterceptor = mock(ServerResponseInterceptor.class);

        serverThreadBinder = mock(ServerSpanThreadBinder.class);
        subject = new ServletHandlerInterceptor(requestInterceptor, responseInterceptor, spanNameProvider, serverThreadBinder);
    }

    @Test
    public void afterCompletionShouldNotifyOfCompletion() {
        subject.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), this, null);

        verify(responseInterceptor).handle(any(ServerResponseAdapter.class));
    }

    @Test
    public void afterCompletionShouldResetServerTraceOnAsyncCalls() {
        final ServerSpan span = mock(ServerSpan.class);

        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ServletHandlerInterceptor.HTTP_SERVER_SPAN_ATTRIBUTE, span);

        subject.afterCompletion(request, new MockHttpServletResponse(), this, null);

        final InOrder order = inOrder(serverThreadBinder, responseInterceptor);

        order.verify(serverThreadBinder).setCurrentSpan(same(span));
        order.verify(responseInterceptor).handle(any(ServerResponseAdapter.class));
    }

    @Test
    public void afterConcurrentHandlingStartedShouldSaveStateAndClear() {

        final ServerSpan serverSpan = mock(ServerSpan.class);

        when(serverThreadBinder.getCurrentServerSpan()).thenReturn(serverSpan);

        final MockHttpServletRequest request = new MockHttpServletRequest();
        subject.afterConcurrentHandlingStarted(request, new MockHttpServletResponse(), this);

        assertSame(serverSpan, request.getAttribute(ServletHandlerInterceptor.HTTP_SERVER_SPAN_ATTRIBUTE));

        verify(serverThreadBinder).setCurrentSpan(null);
    }
}