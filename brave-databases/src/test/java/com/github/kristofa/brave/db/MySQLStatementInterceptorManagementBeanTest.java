package com.github.kristofa.brave.db;

import com.github.kristofa.brave.ClientTracer;
import com.google.common.base.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class MySQLStatementInterceptorManagementBeanTest {

    private ClientTracer clientTracer;

    @Before
    public void setup() {
        clientTracer = mock(ClientTracer.class);
    }

    @After
    public void clearStatementInterceptor() {
        MySQLStatementInterceptor.setClientTracer(Optional.<ClientTracer>absent());
    }

    @Test
    public void afterPropertiesSetShouldSetTracerOnStatementInterceptor() {
        new MySQLStatementInterceptorManagementBean(clientTracer);
        assertSame(clientTracer, MySQLStatementInterceptor.clientTracer.get());
    }

    @Test
    public void closeShouldCleanUpStatementInterceptor() throws IOException {

        final MySQLStatementInterceptorManagementBean subject = new MySQLStatementInterceptorManagementBean(clientTracer);

        MySQLStatementInterceptor.setClientTracer(Optional.of(clientTracer));

        subject.close();

        assertEquals(Optional.<ClientTracer>absent(), MySQLStatementInterceptor.clientTracer);
    }

}
