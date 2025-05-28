/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import brave.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class TracingSqlLoggerTest {
  @Mock Span span;

  @AfterEach void after() {
    verifyNoMoreInteractions(span);
  }

  @Test void parseServerIpAndPort_dontAddServiceName() {
    TracingSqlLogger.parseServerIpAndPort("jdbc:mysql://localhost:3306/testdb", span, false);

    verify(span).remoteIpAndPort("localhost", 3306);
  }

  @Test void parseServerIpAndPort_h2() {
    TracingSqlLogger.parseServerIpAndPort("jdbc:h2:mem:testdb", span, true);

    verify(span).remoteServiceName("h2-testdb");
  }

  @Test void parseServerIpAndPort_mysql() {
    TracingSqlLogger.parseServerIpAndPort("jdbc:mysql://localhost:3306/testdb", span, true);

    verify(span).remoteServiceName("mysql-testdb");
    verify(span).remoteIpAndPort("localhost", 3306);
  }

  @Test void parseServerIpAndPort_oracle() {
    TracingSqlLogger.parseServerIpAndPort("jdbc:oracle:thin:@localhost:1521:testdb", span, true);

    verify(span).remoteServiceName("oracle-testdb");
  }

  @Test void parseServerIpAndPort_oracle_params() {
    TracingSqlLogger.parseServerIpAndPort(
      "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=myhost)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=myservice)))",
      span, true);

    verify(span).remoteServiceName(
      "oracle-@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=myhost)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=myservice)))"
    );
  }

  @Test void parseServerIpAndPort_postgresql() {
    TracingSqlLogger.parseServerIpAndPort("jdbc:postgresql://localhost:5432/testdb", span, true);

    verify(span).remoteIpAndPort("localhost", 5432);
    verify(span).remoteServiceName("postgresql-testdb");
  }

  @Test void parseServerIpAndPort_sqlite() {
    TracingSqlLogger.parseServerIpAndPort("jdbc:sqlite:test.db", span, true);

    verify(span).remoteServiceName("sqlite-test.db");
  }

  @Test void parseServerIpAndPort_invalid() {
    TracingSqlLogger.parseServerIpAndPort("jobc", span, false);

    // expect nothing to happen
  }
}
