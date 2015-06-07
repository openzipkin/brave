package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.ClientTracer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BraveClientResponseFilterTest {

    @Mock
    private ClientTracer clientTracer;
    private BraveClientResponseFilter braveClientResponseFilter;
    @Mock
    private ClientRequestContext clientRequestContext;
    @Mock
    private ClientResponseContext clientResponseContext;

    @Before
    public void setUp() throws Exception {
        braveClientResponseFilter = new BraveClientResponseFilter(clientTracer, null);
    }

    @Test
    public void testFilter_200() throws Exception {
        final int responseStatus = 200;

        when(clientResponseContext.getStatus()).thenReturn(responseStatus);

        braveClientResponseFilter.filter(clientRequestContext, clientResponseContext);

        verify(clientResponseContext).getStatus();
        verify(clientTracer).submitBinaryAnnotation("http.responsecode", responseStatus);
        verify(clientTracer).setClientReceived();

        verifyNoMoreInteractions(clientTracer);
        verifyNoMoreInteractions(clientRequestContext);
        verifyNoMoreInteractions(clientResponseContext);
    }

    @Test
    public void testFilter_500() throws Exception {
        final int responseStatus = 500;

        when(clientResponseContext.getStatus()).thenReturn(responseStatus);

        braveClientResponseFilter.filter(clientRequestContext, clientResponseContext);

        verify(clientResponseContext).getStatus();
        verify(clientTracer).submitBinaryAnnotation("http.responsecode", responseStatus);
        verify(clientTracer).setClientReceived();
        verify(clientTracer).submitAnnotation("failure");

        verifyNoMoreInteractions(clientTracer);
        verifyNoMoreInteractions(clientRequestContext);
        verifyNoMoreInteractions(clientResponseContext);
    }
}