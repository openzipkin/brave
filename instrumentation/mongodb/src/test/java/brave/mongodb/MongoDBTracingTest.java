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
package brave.mongodb;

import brave.Tracer;
import brave.Tracing;
import com.mongodb.event.CommandListener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MongoDBTracingTest {
  @Mock Tracing tracing;

  @Test public void create_buildsWithDefaults() {
    MongoDBTracing mongoDBTracing = MongoDBTracing.create(tracing);

    assertThat(mongoDBTracing).extracting("tracing").isEqualTo(tracing);
  }

  @Test public void newBuilder_setsValuesCorrectly() {
    MongoDBTracing mongoDBTracing = MongoDBTracing.newBuilder(tracing).build();

    assertThat(mongoDBTracing).extracting("tracing").isEqualTo(tracing);
  }

  @Test public void commandListener_returnsTraceMongoCommandListener() {
    Tracer tracer = mock(Tracer.class);
    when(tracing.tracer()).thenReturn(tracer);

    CommandListener listener = MongoDBTracing.newBuilder(tracing).build().commandListener();
    assertThat(listener).isInstanceOf(TraceMongoCommandListener.class);
    assertThat(listener).extracting("threadLocalSpan").extracting("tracer").isEqualTo(tracer);
  }
}
