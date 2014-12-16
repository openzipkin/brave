package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.ServerTracer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class BraveContainerResponseFilterTest {

    @Mock
    private ServerTracer serverTracer;

    @InjectMocks
    private BraveContainerResponseFilter containerResponseFilter = new BraveContainerResponseFilter(serverTracer);

    @Test
    public void testResponseFilter() throws IOException {
        containerResponseFilter.filter(null, null);
        verify(serverTracer).setServerSend();
        verify(serverTracer).clearCurrentSpan();
    }
}
