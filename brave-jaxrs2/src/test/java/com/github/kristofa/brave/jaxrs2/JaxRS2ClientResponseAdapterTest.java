package com.github.kristofa.brave.jaxrs2;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ws.rs.client.ClientResponseContext;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JaxRS2ClientResponseAdapterTest {

    @Mock
    private ClientResponseContext response;
    private JaxRS2ClientResponseAdapter jaxRS2ClientResponseAdapter;

    @Before
    public void setUp() throws Exception {
        when(response.getStatus()).thenReturn(200);
        jaxRS2ClientResponseAdapter = new JaxRS2ClientResponseAdapter(response);
    }

    @Test
    public void testGetStatusCode() throws Exception {
        assertThat(jaxRS2ClientResponseAdapter.getStatusCode(), is(200));

        verify(response).getStatus();
        verifyNoMoreInteractions(response);
    }
}