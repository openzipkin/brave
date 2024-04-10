/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.dubbo;

import brave.internal.Platform;
import brave.propagation.Propagation;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.service.GenericService;

class TestServer {
  final BlockingQueue<TraceContextOrSamplingFlags> requestQueue = new LinkedBlockingQueue<>();
  final Extractor<Map<String, String>> extractor;
  final ServiceConfig<GenericService> service;
  final String linkLocalIp;

  TestServer(Propagation.Factory propagationFactory, ApplicationConfig application) {
    extractor = propagationFactory.get().extractor(Map::get);
    linkLocalIp = Platform.get().linkLocalIp();
    if (linkLocalIp != null) {
      // avoid dubbo's logic which might pick docker ip
      System.setProperty(CommonConstants.DUBBO_IP_TO_BIND, linkLocalIp);
      System.setProperty(CommonConstants.DubboProperty.DUBBO_IP_TO_REGISTRY, linkLocalIp);
    }
    // reduce dubbo shutdown timeout to 1s
    System.setProperty(CommonConstants.SHUTDOWN_WAIT_KEY, "1000");
    service = new ServiceConfig<>();
    service.setApplication(application);
    service.setRegistry(new RegistryConfig(RegistryConfig.NO_AVAILABLE));
    service.setProtocol(new ProtocolConfig("dubbo", PickUnusedPort.get()));
  }

  public void initService() {
    service.setInterface(GreeterService.class);
    service.setRef((method, parameterTypes, args) -> {
      requestQueue.add(extractor.extract(RpcContext.getContext().getAttachments()));
      return args[0];
    });
  }


  void start() {
    service.export();
  }

  void stop() {
    service.unexport();
  }

  int port() {
    return service.getProtocol().getPort();
  }

  TraceContextOrSamplingFlags takeRequest() {
    try {
      return requestQueue.poll(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  String ip() {
    return linkLocalIp != null ? linkLocalIp : "127.0.0.1";
  }
}
