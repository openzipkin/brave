package com.github.kristofa.brave;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

public class BraveRunnableTest {

    private BraveRunnable braveRunnable;
    private Runnable mockWrappedRunnable;
    private ServerSpanThreadBinder mockThreadBinder;
    private ServerSpan mockSpan;

    @Before
    public void setup() {
        mockWrappedRunnable = mock(Runnable.class);
        mockThreadBinder = mock(ServerSpanThreadBinder.class);
        mockSpan = mock(ServerSpan.class);
        when(mockThreadBinder.getCurrentServerSpan()).thenReturn(mockSpan);
        braveRunnable = BraveRunnable.create(mockWrappedRunnable, mockThreadBinder);
    }

    @Test
    public void testRun() throws Exception {
        braveRunnable.run();

        final InOrder inOrder = inOrder(mockWrappedRunnable, mockThreadBinder, mockSpan);
        inOrder.verify(mockThreadBinder).getCurrentServerSpan();
        inOrder.verify(mockThreadBinder).setCurrentSpan(mockSpan);
        inOrder.verify(mockWrappedRunnable).run();

        verifyNoMoreInteractions(mockWrappedRunnable, mockThreadBinder, mockSpan);
    }

}
