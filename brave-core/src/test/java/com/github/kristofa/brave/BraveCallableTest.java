package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

public class BraveCallableTest {

    private static final String THREAD_RETURN_VALUE = "returnValue";
    private BraveCallable<String> braveCallable;
    private Callable<String> mockWrappedCallable;
    private ServerSpanThreadBinder mockThreadBinder;
    private ServerSpan mockServerSpan;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        mockWrappedCallable = mock(Callable.class);
        mockThreadBinder = mock(ServerSpanThreadBinder.class);
        mockServerSpan = mock(ServerSpan.class);
        when(mockThreadBinder.getCurrentServerSpan()).thenReturn(mockServerSpan);
        braveCallable = BraveCallable.create(mockWrappedCallable, mockThreadBinder);
    }

    @Test
    public void testCall() throws Exception {
        when(mockWrappedCallable.call()).thenReturn(THREAD_RETURN_VALUE);
        assertEquals(THREAD_RETURN_VALUE, braveCallable.call());

        final InOrder inOrder = inOrder(mockWrappedCallable, mockThreadBinder, mockServerSpan);

        inOrder.verify(mockThreadBinder).getCurrentServerSpan();
        inOrder.verify(mockThreadBinder).setCurrentSpan(mockServerSpan);
        inOrder.verify(mockWrappedCallable).call();
        inOrder.verify(mockServerSpan).incThreadDuration(anyLong());

        verifyNoMoreInteractions(mockWrappedCallable, mockThreadBinder, mockServerSpan);
    }

}
