/*
 * Copyright 2013-2024 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.rocketmq.client;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

final class RocketMQContainer extends GenericContainer<RocketMQContainer> {
  static final int NAMESERVER_PORT = 9876;
  static final int BROKER_PORT = 10911;

  RocketMQContainer() {
    super(DockerImageName.parse("apache/rocketmq:5.1.4"));
    List<String> portBindings = new ArrayList<>();
    portBindings.add(String.format("%d:%d", NAMESERVER_PORT, NAMESERVER_PORT));
    portBindings.add(String.format("%d:%d", BROKER_PORT, BROKER_PORT));
    setPortBindings(portBindings);

    // do not publish all ports
    withCreateContainerCmdModifier(cmd -> {
      if (cmd.getHostConfig() != null) {
        cmd.getHostConfig().withPublishAllPorts(false);
      }
    });

    setCommand("sh /start.sh");
    this.waitStrategy =
      Wait.forLogMessage(".*boot success.*", 1).withStartupTimeout(Duration.ofSeconds(60));

    mount("broker.conf");
    mount("start.sh");
  }

  private void mount(String fileName) {
    URL confUrl = getClass().getClassLoader().getResource(fileName);
    try {
      String confPath = new File(confUrl.toURI()).getAbsolutePath();
      withFileSystemBind(confPath, "/" + fileName, BindMode.READ_ONLY);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  public String getNamesrvAddr() {
    return getHost() + ":" + NAMESERVER_PORT;
  }
}
