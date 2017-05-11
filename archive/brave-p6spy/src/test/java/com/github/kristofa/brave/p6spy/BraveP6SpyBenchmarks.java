package com.github.kristofa.brave.p6spy;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.sql.ResultSet;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for testing the overall impact of using the p6 driver spy with Zipkin tracing enabled vs. the raw JDBC
 * driver.
 */
@Measurement(iterations = 10, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Threads(1)
@State(Scope.Benchmark)
public class BraveP6SpyBenchmarks {

    //Get rid of annoying derby.log
    static {
        DerbyUtils.disableLog();
    }

    private static final String INSERT_SQL = "insert into customers values (?, ?)";
    private static final String QUERY_SQL = "select * from customers order by id asc FETCH NEXT 10 ROWS ONLY";

    private static int ROW_SEED = 10;

    private TestDatabaseRule rawDerbyDb;
    private TestDatabaseRule spiedDerbyDb;

    @Setup(Level.Trial)
    public void setup() throws Throwable {
        rawDerbyDb = new TestDatabaseRule("jdbc:derby:memory:p6spy;create=true", "whatever", "");
        rawDerbyDb.before();

        spiedDerbyDb = new TestDatabaseRule();
        spiedDerbyDb.before();

        //Prepopulate some rows in the database
        for (int i = 0; i < 10000; i++) {
            runInsertion(rawDerbyDb);
            runInsertion(spiedDerbyDb);
        }
    }

    @Benchmark
    @OperationsPerInvocation(1000)
    public void raw_jdbc_inserts() throws Throwable {
        runInsertion(rawDerbyDb);
    }

    @Benchmark
    @OperationsPerInvocation(1000)
    public void spied_jdbc_inserts() throws Throwable {
        runInsertion(spiedDerbyDb);
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public void raw_jdbc_query() throws Throwable {
        runQuery(rawDerbyDb);
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public void spied_jdbc_query() throws Throwable {
        runQuery(spiedDerbyDb);
    }

    private void runInsertion(TestDatabaseRule testDatabaseRule) {
        testDatabaseRule.executePreparedStatement(ps -> {
            ps.setInt(1, ++ROW_SEED);
            ps.setString(2, "test");
            int rowCount = ps.executeUpdate();
            assert rowCount == 1;
        }, INSERT_SQL);
    }

    private void runQuery(TestDatabaseRule testDatabaseRule) {
        testDatabaseRule.executePreparedStatement(ps -> {
            try (ResultSet resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    resultSet.getInt(1);
                }
            }
        }, QUERY_SQL);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(".*" + BraveP6SpyBenchmarks.class.getSimpleName() + ".*")
            .build();

        new Runner(opt).run();
    }

}
