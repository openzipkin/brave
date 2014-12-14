package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.google.common.base.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.core.MultivaluedMap;

import java.net.URI;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JaxRS2ClientRequestAdapterTest {

    public static final String TEST_SPAN_NAME = "testSpanName";
    @Mock
    private ClientRequestContext request;

    @Mock
    private MultivaluedMap<String, Object> headers;

    private JaxRS2ClientRequestAdapter jaxRS2ClientRequestAdapter;
    private URI uri;

    @Before
    public void setUp() throws Exception {
        uri = new URI("/some-uri");
        when(request.getUri()).thenReturn(uri);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeaders()).thenReturn(headers);
        when(headers.getFirst(BraveHttpHeaders.SpanName.getName())).thenReturn(TEST_SPAN_NAME);

        jaxRS2ClientRequestAdapter = new JaxRS2ClientRequestAdapter(request);
    }

    @Test
    public void testGetUri() throws Exception {
        assertThat(jaxRS2ClientRequestAdapter.getUri(), sameInstance(uri));

        verify(request).getUri();
        verifyNoMoreInteractions(request);
        verifyNoMoreInteractions(headers);
    }

    @Test
    public void testGetMethod() throws Exception {
        assertThat(jaxRS2ClientRequestAdapter.getMethod(), is("GET"));

        verify(request).getMethod();
        verifyNoMoreInteractions(request);
        verifyNoMoreInteractions(headers);
    }

    @Test
    public void testGetSpanName() throws Exception {
        assertThat(jaxRS2ClientRequestAdapter.getSpanName(), is(Optional.of(TEST_SPAN_NAME)));

        verify(request).getHeaders();
        verify(headers).getFirst(BraveHttpHeaders.SpanName.getName());

        verifyNoMoreInteractions(request);
        verifyNoMoreInteractions(headers);
    }

    @Test
    public void testAddHeader() throws Exception {
        final String someHeader = "SomeHeader";
        final String someValue = "SomeValue";
        jaxRS2ClientRequestAdapter.addHeader(someHeader, someValue);

        verify(request).getHeaders();
        verify(headers).add(someHeader, someValue);

        verifyNoMoreInteractions(request);
        verifyNoMoreInteractions(headers);
    }
}