package com.github.kristofa.brave.mysql;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.EndpointSubmitter;
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;
import com.mysql.jdbc.ResultSetInternalMethods;
import com.mysql.jdbc.Statement;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.sql.SQLException;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class MySQLStatementInterceptorTest {

    private final EndpointSubmitter endpointSubmitter = mock(EndpointSubmitter.class);
    private final ClientTracer clientTracer = mock(ClientTracer.class);
    private MySQLStatementInterceptor subject;

    @Before
    public void setUp() throws Exception {
        subject = new MySQLStatementInterceptor();
        MySQLStatementInterceptor.setClientTracer(clientTracer);
        MySQLStatementInterceptor.setEndpointSubmitter(endpointSubmitter);
    }

    @Test
    public void preProcessShouldNotFailIfNoClientTracer() throws Exception {
        MySQLStatementInterceptor.setClientTracer(null);

        assertNull(subject.preProcess("sql", mock(Statement.class), mock(Connection.class)));

        verifyZeroInteractions(clientTracer);
    }

    @Test
    public void preProcessShouldNotFailIfEndPointNotPresent() throws Exception {
        MySQLStatementInterceptor.setEndpointSubmitter(null);

        assertNull(subject.preProcess("sql", mock(Statement.class), mock(Connection.class)));

        verifyZeroInteractions(clientTracer);
    }

    @Test
    public void preProcessShouldNotFailIfEndPointNotSubmitted() throws Exception {
        when(endpointSubmitter.endpointSubmitted()).thenReturn(false);

        assertNull(subject.preProcess("sql", mock(Statement.class), mock(Connection.class)));

        verifyZeroInteractions(clientTracer);
    }

    @Test
    public void preProcessShouldBeginTracingSQLCall() throws Exception {
        final String sql = randomAlphanumeric(20);
        final String schema = randomAlphanumeric(20);

        final Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn(schema);

        when(endpointSubmitter.endpointSubmitted()).thenReturn(true);

        assertNull(subject.preProcess(sql, mock(Statement.class), connection));

        final InOrder order = inOrder(clientTracer);

        order.verify(clientTracer).startNewSpan("query");
        order.verify(clientTracer).setCurrentClientServiceName(eq(schema));
        order.verify(clientTracer).submitBinaryAnnotation(eq("executed.query"), eq(sql));
        order.verify(clientTracer).setClientSent();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void preProcessShouldBeginTracingPreparedStatementCall() throws Exception {
        final String sql = randomAlphanumeric(20);
        final String schema = randomAlphanumeric(20);

        final PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.getPreparedSql()).thenReturn(sql);
        final Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn(schema);

        when(endpointSubmitter.endpointSubmitted()).thenReturn(true);

        assertNull(subject.preProcess(null, statement, connection));

        final InOrder order = inOrder(clientTracer);

        order.verify(clientTracer).startNewSpan("query");
        order.verify(clientTracer).setCurrentClientServiceName(eq(schema));
        order.verify(clientTracer).submitBinaryAnnotation(eq("executed.query"), eq(sql));
        order.verify(clientTracer).setClientSent();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void postProcessShouldNotFailIfNoClientTracer() throws Exception {
        MySQLStatementInterceptor.setClientTracer(null);

        assertNull(subject.postProcess("sql", mock(Statement.class), mock(ResultSetInternalMethods.class), mock(Connection.class), 1, true, true, null));

        verifyZeroInteractions(clientTracer);
    }

    @Test
    public void postProcessShouldNotFailIfEndPointNotPresent() throws Exception {
        MySQLStatementInterceptor.setEndpointSubmitter(null);

        assertNull(subject.postProcess("sql", mock(Statement.class), mock(ResultSetInternalMethods.class), mock(Connection.class), 1, true, true, null));

        verifyZeroInteractions(clientTracer);
    }

    @Test
    public void postProcessShouldNotFailIfEndPointNotSubmitted() throws Exception {
        when(endpointSubmitter.endpointSubmitted()).thenReturn(false);

        assertNull(subject.postProcess("sql", mock(Statement.class), mock(ResultSetInternalMethods.class), mock(Connection.class), 1, true, true, null));

        verifyZeroInteractions(clientTracer);
    }

    @Test
    public void postProcessShouldFinishTracingFailedSQLCall() throws Exception {

        final int warningCount = 1;
        final int errorCode = 2;

        when(endpointSubmitter.endpointSubmitted()).thenReturn(true);

        assertNull(subject.postProcess("sql", mock(Statement.class), mock(ResultSetInternalMethods.class), mock(Connection.class), warningCount, true, true,
                new SQLException("", "", errorCode)));

        final InOrder order = inOrder(clientTracer);

        order.verify(clientTracer).submitBinaryAnnotation(eq("warning.count"), eq(warningCount));
        order.verify(clientTracer).submitBinaryAnnotation(eq("error.code"), eq(errorCode));
        order.verify(clientTracer).setClientReceived();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void postProcessShouldFinishTracingSuccessfulSQLCall() throws Exception {

        when(endpointSubmitter.endpointSubmitted()).thenReturn(true);

        assertNull(subject.postProcess("sql", mock(Statement.class), mock(ResultSetInternalMethods.class), mock(Connection.class), 0, true, true, null));

        final InOrder order = inOrder(clientTracer);

        order.verify(clientTracer).setClientReceived();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void executeTopLevelOnlyShouldOnlyExecuteTopLevelQueries() throws Exception {
        assertTrue(subject.executeTopLevelOnly());
        verifyZeroInteractions(clientTracer);
    }

}
