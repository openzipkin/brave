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

import brave.test.ITRemote;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.Filter;
import org.junit.After;

public abstract class ITTracingFilter extends ITRemote {
  ApplicationConfig application = new ApplicationConfig("brave");
  TestServer server = new TestServer(propagationFactory, application);
  ReferenceConfig<GreeterService> client;

  @After public void stop() {
    if (client != null) client.destroy();
    server.stop();
  }

  /** Call this after updating {@link #tracing} */
  TracingFilter init() {
    TracingFilter filter = (TracingFilter) ExtensionLoader.getExtensionLoader(Filter.class)
      .getExtension("tracing");
    filter.setTracing(tracing);
    return filter;
  }
}
