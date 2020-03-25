/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.dubbo;

import brave.internal.Platform;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.Constants;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.service.GenericService;

class TestServer {
  final BlockingQueue<TraceContextOrSamplingFlags> requestQueue = new LinkedBlockingQueue<>();
  final TraceContext.Extractor<DubboServerRequest> extractor;
  final ServiceConfig<GenericService> service;
  final String linkLocalIp;

  TestServer(Propagation.Factory propagationFactory, ApplicationConfig application) {
    extractor =
      propagationFactory.create(Propagation.KeyFactory.STRING).extractor(DubboServerRequest.GETTER);
    linkLocalIp = Platform.get().linkLocalIp();
    if (linkLocalIp != null) {
      // avoid dubbo's logic which might pick docker ip
      System.setProperty(CommonConstants.DUBBO_IP_TO_BIND, linkLocalIp);
      System.setProperty(Constants.DUBBO_IP_TO_REGISTRY, linkLocalIp);
    }
    service = new ServiceConfig<>();
    service.setApplication(application);
    service.setRegistry(new RegistryConfig(RegistryConfig.NO_AVAILABLE));
    service.setProtocol(new ProtocolConfig("dubbo", PickUnusedPort.get()));
    service.setInterface(GreeterService.class);
    service.setRef((method, parameterTypes, args) -> {
      RpcContext context = RpcContext.getContext();
      DubboServerRequest request =
        new DubboServerRequest(context.getInvocation(), context.getAttachments());
      requestQueue.add(extractor.extract(request));

      return args[0];
    });
  }

  ApplicationConfig application() {
    return service.getApplication();
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
