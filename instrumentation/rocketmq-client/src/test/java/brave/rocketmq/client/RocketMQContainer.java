/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rocketmq.client;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class RocketMQContainer extends GenericContainer<RocketMQContainer> {
  static Logger LOGGER = LoggerFactory.getLogger(RocketMQContainer.class);
  static final int NAMESERVER_PORT = 9876;
  static final int BROKER_PORT = 10911;

  RocketMQContainer() {
    super(DockerImageName.parse("apache/rocketmq:5.3.1"));
    List<String> portBindings = new ArrayList<>();
    portBindings.add(String.format("%d:%d", NAMESERVER_PORT, NAMESERVER_PORT));
    portBindings.add(String.format("%d:%d", BROKER_PORT, BROKER_PORT));
    setPortBindings(portBindings);
    setCommand("/bin/sh", "-c", "sh mqnamesrv & sh mqbroker -n localhost:" + NAMESERVER_PORT);
    this.waitStrategy = Wait.forLogMessage(".*boot success.*", 1)
        .withStartupTimeout(Duration.ofSeconds(60));
  }

  @Override
  protected void containerIsStarted(InspectContainerResponse containerInfo) {
    followOutput(new Slf4jLogConsumer(LOGGER));
    List<String> updateBrokerConfigCommands = new ArrayList<>();
    updateBrokerConfigCommands.add(updateBrokerConfig("brokerIP1", getHost()));
    updateBrokerConfigCommands.add(updateBrokerConfig("listenPort", BROKER_PORT));
    updateBrokerConfigCommands.add(updateBrokerConfig("autoCreateTopicEnable", true));
    final String command = String.join(" && ", updateBrokerConfigCommands);
    ExecResult result;
    try {
      result = execInContainer("/bin/sh", "-c", command);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    if (result != null && result.getExitCode() != 0) {
      throw new IllegalStateException(result.toString());
    }
  }

  String updateBrokerConfig(final String key, final Object val) {
    return String.format("./mqadmin updateBrokerConfig -b localhost:%s -k %s -v %s", BROKER_PORT, key, val);
  }

  String getNamesrvAddr() {
    return getHost() + ":" + NAMESERVER_PORT;
  }
}
