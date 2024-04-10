/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.dubbo;

import brave.rpc.RpcTracing;
import brave.test.ITRemote;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.Filter;
import org.junit.jupiter.api.AfterEach;

import static org.apache.dubbo.common.utils.SerializeCheckStatus.DISABLE;

public abstract class ITTracingFilter extends ITRemote {
  ApplicationConfig application = getApplicationConfig();

  static ApplicationConfig getApplicationConfig() {
    ApplicationConfig application = new ApplicationConfig("brave");
    // Allow in tests serializers like org.apache.dubbo.common.beanutil.JavaBeanDescriptor
    application.setSerializeCheckStatus(DISABLE.name());
    return application;
  }

  TestServer server = new TestServer(propagationFactory, application);
  ReferenceConfig<GreeterService> client;

  @AfterEach void stop() {
    if (client != null) client.destroy();
    server.stop();
  }

  /** Call this after updating {@link #tracing} */
  TracingFilter init() {
    TracingFilter filter = (TracingFilter) ExtensionLoader.getExtensionLoader(Filter.class)
      .getExtension("tracing");
    filter.setRpcTracing(RpcTracing.create(tracing));
    return filter;
  }
}
