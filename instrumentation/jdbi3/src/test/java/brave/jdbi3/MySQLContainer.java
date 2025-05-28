/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import com.mysql.cj.jdbc.MysqlDataSource;
import java.time.Duration;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.testcontainers.utility.DockerImageName.parse;

final class MySQLContainer extends GenericContainer<MySQLContainer> {
  static final Logger LOGGER = LoggerFactory.getLogger(MySQLContainer.class);
  static final int MYSQL_PORT = 3306;

  MySQLContainer() {
    // Use OpenZipkin's small test image, which is multi-arch and doesn't consume Docker Hub quota
    super(parse("ghcr.io/openzipkin/zipkin-mysql:3.4.3"));
    if ("true".equals(System.getProperty("docker.skip"))) {
      throw new TestAbortedException("${docker.skip} == true");
    }
    withExposedPorts(MYSQL_PORT);
    waitStrategy = Wait.forHealthcheck();
    withStartupTimeout(Duration.ofSeconds(60));
    withLogConsumer(new Slf4jLogConsumer(LOGGER));
  }

  MysqlDataSource dataSource() {
    MysqlDataSource dataSource = new MysqlDataSource();
    dataSource.setUrl(
      String.format(
        "jdbc:mysql://%s:%s/zipkin?autoReconnect=true&useUnicode=yes&characterEncoding=UTF-8",
        host(), port()));
    dataSource.setUser("zipkin");
    dataSource.setPassword("zipkin");
    return dataSource;
  }

  /**
   * Return an IP address to ensure remote IP checks work.
   */
  String host() {
    return "127.0.0.1";
  }

  int port() {
    return getMappedPort(MYSQL_PORT);
  }
}
