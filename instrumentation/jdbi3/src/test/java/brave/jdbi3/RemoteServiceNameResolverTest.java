/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class RemoteServiceNameResolverTest {

    @ParameterizedTest(name = "DISPLAY_NAME_PLACEHOLDER" + "{0}")
    @CsvSource({
        "jdbc:mysql://localhost:3306/testdb, localhost:3306",
        "jdbc:mysql://localhost:3306/testdb?zipkinServiceName=test, test",
        "jdbc:postgresql://localhost:5432/testdb, localhost:5432",
        "jdbc:postgresql://localhost:5432/testdb?zipkinServiceName=test, test",
        "jdbc:h2:mem:testdb, h2",
        "jdbc:sqlite:test.db, sqlite",
        "jdbc:sqlite:test.db?zipkinServiceName=test, test",
        "jdbc:oracle:thin:@localhost:1521:testdb, oracle",
        "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=myhost)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=myservice))), oracle",
        "jdbc:oracle:oci:@//myhost.example.com:1521/MYSERVICE?zipkinServiceName=oracledb, oracledb"
    })
    public void shouldParseRemoteServiceName(String url, String expected) throws SQLException {
        RemoteServiceNameResolver resolver = new RemoteServiceNameResolver();

        String remoteServiceName = resolver.resolve(url);

        assertThat(remoteServiceName).isEqualTo(expected);
    }
}
