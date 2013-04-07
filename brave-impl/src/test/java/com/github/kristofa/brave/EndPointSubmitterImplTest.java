package com.github.kristofa.brave;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

public class EndPointSubmitterImplTest {

    private final static int IP = 11;
    private final static int PORT = 8080;
    private final static String SERVICE_NAME = "serviceName";

    private CommonSpanState mockState;
    private EndPoint mockEndPoint;
    private EndPointSubmitterImpl endPointSubmitter;

    @Before
    public void setUp() {
        mockState = mock(CommonSpanState.class);
        mockEndPoint = mock(EndPoint.class);
        endPointSubmitter = new EndPointSubmitterImpl(mockState);
    }

    @Test(expected = NullPointerException.class)
    public void testEndPointSubmitterImplNullState() {
        new EndPointSubmitterImpl(null);
    }

    @Test
    public void testSubmit() {
        endPointSubmitter.submit(IP, PORT, SERVICE_NAME);
        final EndPoint expectedEndPoint = new EndPointImpl(IP, PORT, SERVICE_NAME);
        verify(mockState).setEndPoint(expectedEndPoint);
        verifyNoMoreInteractions(mockState, mockEndPoint);
    }

    @Test
    public void testGetEndPoint() {
        when(mockState.getEndPoint()).thenReturn(mockEndPoint);
        assertSame(mockEndPoint, endPointSubmitter.getEndPoint());
        verify(mockState).getEndPoint();
        verifyNoMoreInteractions(mockState, mockEndPoint);
    }

}
