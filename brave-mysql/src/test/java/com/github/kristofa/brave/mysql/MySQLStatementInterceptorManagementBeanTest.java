package com.github.kristofa.brave.mysql;

import com.github.kristofa.brave.ClientTracer;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class MySQLStatementInterceptorManagementBeanTest {

    private final ClientTracer clientTracer = mock(ClientTracer.class);

    @After
    public void clearStatementInterceptor() {
        MySQLStatementInterceptor.setClientTracer(null);
    }

    @Test
    public void afterPropertiesSetShouldSetTracerOnStatementInterceptor() {
        new MySQLStatementInterceptorManagementBean(clientTracer);
        assertSame(clientTracer, MySQLStatementInterceptor.clientTracer);
    }

    @Test
    public void closeShouldCleanUpStatementInterceptor() throws IOException {
        final MySQLStatementInterceptorManagementBean subject = new MySQLStatementInterceptorManagementBean(clientTracer);
        MySQLStatementInterceptor.setClientTracer(clientTracer);
        subject.close();
         assertNull(MySQLStatementInterceptor.clientTracer);
    }

}
