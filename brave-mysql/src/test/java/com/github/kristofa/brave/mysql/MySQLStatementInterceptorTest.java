package com.github.kristofa.brave.mysql;

import com.github.kristofa.brave.ClientTracer;
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

    private MySQLStatementInterceptor subject;
    private ClientTracer clientTracer;

    @Before
    public void setUp() throws Exception {
        subject = new MySQLStatementInterceptor();
        clientTracer = mock(ClientTracer.class);
        MySQLStatementInterceptor.setClientTracer(clientTracer);
    }

    @Test
    public void preProcessShouldNotFailIfNoClientTracer() throws Exception {
        MySQLStatementInterceptor.setClientTracer(null);

        assertNull(subject.preProcess("sql", mock(Statement.class), mock(Connection.class)));

        verifyZeroInteractions(clientTracer);
    }

    @Test
    public void preProcessShouldBeginTracingSQLCall() throws Exception {
        final String sql = randomAlphanumeric(20);
        final String schema = randomAlphanumeric(20);

        final Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn(schema);

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
    public void postProcessShouldFinishTracingFailedSQLCall() throws Exception {

        final int warningCount = 1;
        final int errorCode = 2;

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
