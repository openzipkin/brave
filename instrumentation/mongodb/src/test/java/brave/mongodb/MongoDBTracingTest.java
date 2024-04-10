/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.mongodb;

import brave.Tracer;
import brave.Tracing;
import com.mongodb.event.CommandListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MongoDBTracingTest {
  @Mock Tracing tracing;

  @Test void create_buildsWithDefaults() {
    MongoDBTracing mongoDBTracing = MongoDBTracing.create(tracing);

    assertThat(mongoDBTracing).extracting("tracing").isEqualTo(tracing);
  }

  @Test void newBuilder_setsValuesCorrectly() {
    MongoDBTracing mongoDBTracing = MongoDBTracing.newBuilder(tracing).build();

    assertThat(mongoDBTracing).extracting("tracing").isEqualTo(tracing);
  }

  @Test void commandListener_returnsTraceMongoCommandListener() {
    Tracer tracer = mock(Tracer.class);
    when(tracing.tracer()).thenReturn(tracer);

    CommandListener listener = MongoDBTracing.newBuilder(tracing).build().commandListener();
    assertThat(listener).isInstanceOf(TraceMongoCommandListener.class);
    assertThat(listener).extracting("threadLocalSpan").extracting("tracer").isEqualTo(tracer);
  }
}
