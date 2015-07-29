package com.github.kristofa.brave.client;

import org.hamcrest.Description;
import org.junit.Test;
import org.junit.internal.matchers.TypeSafeMatcher;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BraveClientHttpRequestInterceptorTest {

    private final ClientRequestInterceptor requestInterceptor = mock(ClientRequestInterceptor.class);
    private final ClientResponseInterceptor responseInterceptor = mock(ClientResponseInterceptor.class);
    private final String serviceName = randomAlphanumeric(20);
    private final BraveClientHttpRequestInterceptor subject = new BraveClientHttpRequestInterceptor(requestInterceptor, responseInterceptor, serviceName);

    @Test
    public void interceptShouldWrapCall() throws IOException {

        final MockClientHttpRequest request = new MockClientHttpRequest();
        final MockClientHttpResponse expected = new MockClientHttpResponse(new byte[0], HttpStatus.I_AM_A_TEAPOT);
        final byte[] body = new byte[12];

        final ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(request, body)).thenReturn(expected);

        assertSame(expected, subject.intercept(request, body, execution));

        final InOrder order = inOrder(requestInterceptor, responseInterceptor, execution);

        order.verify(requestInterceptor).handle(argThat(new TypeSafeMatcher<ClientRequestAdapter>() {
            @Override
            public boolean matchesSafely(final ClientRequestAdapter clientRequestAdapter) {
                return clientRequestAdapter instanceof SpringRequestAdapter && ((SpringRequestAdapter) clientRequestAdapter).getRequest() == request;
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("SpringRequestAdapter{request=").appendValue(request).appendText("}");
            }
        }), eq(serviceName));
        order.verify(execution).execute(request, body);
        order.verify(responseInterceptor).handle(argThat(new TypeSafeMatcher<ClientResponseAdapter>() {
            @Override
            public boolean matchesSafely(final ClientResponseAdapter clientResponseAdapter) {
                return clientResponseAdapter instanceof SpringResponseAdapter && ((SpringResponseAdapter) clientResponseAdapter).getResponse() == expected;
            }

            @Override
            public void describeTo(final Description description) {

            }
        }));
    }

}
