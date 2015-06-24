package com.github.kristofa.brave.mysql;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.EndpointSubmitter;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class MySQLStatementInterceptorManagementBeanTest {

    private final ClientTracer clientTracer = mock(ClientTracer.class);
    private final EndpointSubmitter endpointSubmitter = mock(EndpointSubmitter.class);

    @After
    public void clearStatementInterceptor() {
        MySQLStatementInterceptor.setClientTracer(null);
        MySQLStatementInterceptor.setEndpointSubmitter(null);
    }

    @Test
    public void afterPropertiesSetShouldSetTracerOnStatementInterceptor() {
        new MySQLStatementInterceptorManagementBean(clientTracer, endpointSubmitter);
        assertSame(clientTracer, MySQLStatementInterceptor.clientTracer);
        assertSame(endpointSubmitter, MySQLStatementInterceptor.endpointSubmitter);
    }

    @Test
    public void closeShouldCleanUpStatementInterceptor() throws IOException {

        final MySQLStatementInterceptorManagementBean subject = new MySQLStatementInterceptorManagementBean(clientTracer, endpointSubmitter);

        MySQLStatementInterceptor.setClientTracer(clientTracer);

        subject.close();

        assertNull(MySQLStatementInterceptor.clientTracer);
        assertNull(MySQLStatementInterceptor.endpointSubmitter);
    }

}
