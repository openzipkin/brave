package brave.dubbo.rpc;

import brave.internal.Platform;
import brave.propagation.B3Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.config.ServiceConfig;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.service.GenericService;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class TestServer {
  BlockingQueue<Long> delayQueue = new LinkedBlockingQueue<>();
  BlockingQueue<TraceContextOrSamplingFlags> requestQueue = new LinkedBlockingQueue<>();
  TraceContext.Extractor<Map<String, String>> extractor =
      B3Propagation.B3_STRING.extractor(TracingFilter.GETTER);
  ServiceConfig<GenericService> service;
  String linkLocalIp;

  TestServer() {
    linkLocalIp = Platform.get().linkLocalIp();
    if (linkLocalIp != null) {
      // avoid dubbo's logic which might pick docker ip
      System.setProperty(Constants.DUBBO_IP_TO_BIND, linkLocalIp);
      System.setProperty(Constants.DUBBO_IP_TO_REGISTRY, linkLocalIp);
    }
    service = new ServiceConfig<>();
    service.setApplication(new ApplicationConfig("bean-provider"));
    service.setRegistry(new RegistryConfig(RegistryConfig.NO_AVAILABLE));
    service.setProtocol(new ProtocolConfig("dubbo", PickUnusedPort.get()));
    service.setInterface(GreeterService.class.getName());
    service.setRef((method, parameterTypes, args) -> {
      Long delay = delayQueue.poll();
      if (delay != null) {
        try {
          Thread.sleep(delay);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new AssertionError("interrupted sleeping " + delay);
        }
      }
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

  TraceContextOrSamplingFlags takeRequest() throws InterruptedException {
    return requestQueue.take();
  }

  void enqueueDelay(long millis) {
    this.delayQueue.add(millis);
  }

  String ip() {
    return linkLocalIp != null ? linkLocalIp : "127.0.0.1";
  }
}
