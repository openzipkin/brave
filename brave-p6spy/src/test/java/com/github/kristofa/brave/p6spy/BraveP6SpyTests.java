package com.github.kristofa.brave.p6spy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.github.kristofa.brave.ClientTracer;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.runners.MockitoJUnitRunner;

import zipkin.TraceKeys;

import java.sql.ResultSet;

@RunWith(MockitoJUnitRunner.class)
public class BraveP6SpyTests {

    //Get rid of annoying derby.log
    static {
        DerbyUtils.disableLog();
    }

    private static final String SERVICE_NAME = "customerDb";
    private static final int HOST = -968622079;
    private static final int PORT = 3306;
    private static final String SQL = "select * from customers";
    private static final String PREPARED_SQL = "select * from customers where id > ?";
    private static final String INSERT_SQL = "insert into customers values (12, 'bob')";
    private static final String PREPARED_INSERT_SQL = "insert into customers values (?, ?)";

    @ClassRule
    public static TestDatabaseRule db = new TestDatabaseRule();
    private ClientTracer clientTracer;

    @Before
    public void setup() {
        clientTracer = mock(ClientTracer.class);
        BraveP6SpyListener.setClientTracer(clientTracer);
    }

    @Test
    public void listenerShouldNotFailIfNoClientTracer() throws Exception {
        BraveP6SpyListener.setClientTracer(null);
        db.executeStatement(ps -> {
            boolean result = ps.execute(SQL);
            assertTrue(result);
        });
        verifyZeroInteractions(clientTracer);
    }

    @Test
    public void queryIsTraced() throws Exception {
        db.executeStatement(ps -> {
            ResultSet resultSet = ps.executeQuery(SQL);
            assertTrue(resultSet.next());
            resultSet.close();
        });
        validateTraced(clientTracer, SQL);
    }

    @Test
    public void psIsTraced() throws Exception {
        db.executeStatement(ps -> {
            boolean result = ps.execute(SQL);
            assertTrue(result);
        });
        validateTraced(clientTracer, SQL);
    }

    @Test
    public void preparedStatementIsTraced() throws Exception {
        db.executePreparedStatement(ps -> {
            ps.setInt(1, 0);
            ResultSet resultSet = ps.executeQuery();
            assertTrue(resultSet.next());
            resultSet.close();
        }, PREPARED_SQL);
        validateTraced(clientTracer, PREPARED_SQL);
    }

    @Test
    public void insertStatementIsTraced() throws Exception {
        db.executeStatement(ps -> {
            int count = ps.executeUpdate(INSERT_SQL);
            assertEquals(count, 1);
        });
        validateTraced(clientTracer, INSERT_SQL);
    }

    @Test
    public void preparedInsertStatementIsTraced() throws Exception {
        db.executePreparedStatement(ps -> {
            ps.setInt(1, 13);
            ps.setString(2, "sarah");
            int count = ps.executeUpdate();
            assertEquals(count, 1);
        }, PREPARED_INSERT_SQL);
        validateTraced(clientTracer, PREPARED_INSERT_SQL);
    }

    @Test
    public void batchStatementIsTraced() throws Exception {
        db.executePreparedStatement(ps -> {
            ps.setInt(1, 20);
            ps.setString(2, "barack");
            ps.addBatch();
            ps.setInt(1, 21);
            ps.setString(2, "michelle");
            ps.addBatch();
            int[] count = ps.executeBatch();
            assertEquals(count[0], 1);
            assertEquals(count[1], 1);
        }, PREPARED_INSERT_SQL);
        validateTraced(clientTracer, PREPARED_INSERT_SQL);
    }

    private void validateTraced(ClientTracer clientTracer, String sql) {
        final InOrder order = inOrder(clientTracer);

        order.verify(clientTracer).startNewSpan("query");
        order.verify(clientTracer).submitBinaryAnnotation(eq(TraceKeys.SQL_QUERY), eq(sql));
        order.verify(clientTracer).setClientSent(HOST, PORT, SERVICE_NAME);
        order.verify(clientTracer).setClientReceived();
        order.verifyNoMoreInteractions();
    }

}
