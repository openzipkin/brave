/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static brave.Span.Kind.CLIENT;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.JdbiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mysql.cj.jdbc.MysqlDataSource;

import brave.ScopedSpan;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.StrictCurrentTraceContext;
import brave.test.TestSpanHandler;

public class ITJdbi3BravePlugin {
    protected static final TestSpanHandler spans = new TestSpanHandler();
    protected static final Tracing tracing = Tracing.newBuilder()
        .currentTraceContext(StrictCurrentTraceContext.create())
        .addSpanHandler(spans).build();
    static final String QUERY = "select 'hello world'";
    static final String ERROR_QUERY = "select unknown_field FROM unknown_table";
    protected static Jdbi jdbi = null;

    static int envOr(String key, int fallback) {
        return System.getenv(key) != null ? Integer.parseInt(System.getenv(key)) : fallback;
    }

    static String envOr(String key, String fallback) {
        return System.getenv(key) != null ? System.getenv(key) : fallback;
    }

    public Jdbi buildJdbi() {
        StringBuilder url = new StringBuilder("jdbc:mysql://");
        url.append(envOr("MYSQL_HOST", "127.0.0.1"));
        url.append(":").append(envOr("MYSQL_TCP_PORT", 3306));
        String db = envOr("MYSQL_DB", null);
        if (db != null) {
            url.append("/").append(db);
        }
        url.append("?zipkinServiceName=").append("myservice");
        url.append("&serverTimezone=").append("UTC");

        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(url.toString());

        dataSource.setUser(System.getenv("MYSQL_USER"));
        assumeTrue(dataSource.getUser() != null,
            "Minimally, the environment variable MYSQL_USER must be set");
        dataSource.setPassword(envOr("MYSQL_PASS", ""));

        Jdbi mysql = Jdbi.create(dataSource);
        mysql.installPlugin(Jdbi3BravePlugin.newBuilder(tracing).build());
        return mysql;
    }

    @Test
    void reportsServerAddressForMysqlDatabases() {
        prepareExecuteSelect(jdbi, QUERY);

        assertThat(spans)
            .extracting(MutableSpan::remoteServiceName)
            .contains("myservice");
    }

    @BeforeEach
    void init() {
        if (jdbi != null) {
            return;
        }
        jdbi = buildJdbi();
    }

    @BeforeEach
    void clearSpans() {
        spans.clear();
    }

    @AfterEach
    void close() {
        tracing.close();
    }

    @Test
    void makesChildOfCurrentSpan() {
        ScopedSpan parent = tracing.tracer().startScopedSpan("test");
        try {
            prepareExecuteSelect(jdbi, QUERY);
        } finally {
            parent.finish();
        }

        assertThat(spans)
            .hasSize(2);
    }

    @Test
    void reportsClientKindToZipkin() {
        prepareExecuteSelect(jdbi, QUERY);

        assertThat(spans)
            .extracting(MutableSpan::kind)
            .containsExactly(CLIENT);
    }

    @Test
    void defaultSpanNameIsOperationName() {
        prepareExecuteSelect(jdbi, QUERY);

        assertThat(spans)
            .extracting(MutableSpan::name)
            .containsExactly("Query");
    }

    @Test
    void addsQueryTag() {
        prepareExecuteSelect(jdbi, QUERY);

        assertThat(spans)
            .flatExtracting(s -> s.tags().entrySet())
            .contains(entry("sql.query", QUERY));
    }

    @Test
    void sqlError() {
        assertThatThrownBy(() -> prepareExecuteSelect(jdbi, ERROR_QUERY)).isInstanceOf(JdbiException.class);
        assertThat(spans)
            .isNotEmpty();
    }

    void prepareExecuteSelect(Jdbi jdbi, String query) {
        jdbi.useHandle(h -> {
            h.createQuery(query).mapToMap().list();
        });
    }
}

