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
package brave.propagation.w3c;

import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class TraceContextPropagationClassLoaderTest {
  @Test public void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      Propagation.Factory propagation = TraceContextPropagation.create();
      Injector<Map<String, String>> injector = propagation.get().injector(Map::put);
      Extractor<Map<String, String>> extractor = propagation.get().extractor(Map::get);

      TraceContext context =
          propagation.decorate(TraceContext.newBuilder().traceId(1L).spanId(2L).build());

      Map<String, String> headers = new LinkedHashMap<>();
      injector.inject(context, headers);

      String traceparent = headers.get("traceparent");
      if (!"00-00000000000000000000000000000001-0000000000000002-00".equals(traceparent)) {
        throw new AssertionError();
      }

      if (!context.equals(extractor.extract(headers).context())) {
        throw new AssertionError();
      }
    }
  }
}
