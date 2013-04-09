package com.github.kristofa.brave.resteasy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Before;
import org.junit.Test;

import com.github.kristofa.brave.ServerTracer;

public class BravePostProcessorInterceptorTest {

    private ServerTracer mockServerTracer;
    private BravePostProcessInterceptor interceptor;

    @Before
    public void setUp() throws Exception {
        mockServerTracer = mock(ServerTracer.class);
        interceptor = new BravePostProcessInterceptor(mockServerTracer);
    }

    @Test(expected = NullPointerException.class)
    public void testBravePostProcessInterceptor() {
        new BravePostProcessInterceptor(null);
    }

    @Test
    public void testPostProcess() {

        interceptor.postProcess(null);
        verify(mockServerTracer).setServerSend();
        verifyNoMoreInteractions(mockServerTracer);
    }

}
