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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MongoDBTracingTest {
  @Mock Tracing tracing;

  @Test public void create_buildsWithDefaults() {
    MongoDBTracing mongoDBTracing = MongoDBTracing.create(tracing);
    assertThat(mongoDBTracing).extracting("tracing").isEqualTo(tracing);
    assertThat(mongoDBTracing).extracting("maxAbbreviatedCommandLength").isEqualTo(1000);
    assertThat(mongoDBTracing).extracting("commandsWithCollectionName").asInstanceOf(InstanceOfAssertFactories.ITERABLE)
      .isNotEmpty();
  }

  @Test public void newBuilder_negativeMaxAbbreviatedCommandLength() {
    try {
      MongoDBTracing.newBuilder(tracing).maxAbbreviatedCommandLength(-1);
    } catch (IllegalArgumentException ignored) {
      return;
    }
    fail("Should have thrown IAE due to negative maxAbbreviatedCommandLength");
  }

  @Test public void newBuilder_setsValuesCorrectly() {
    int maxAbbreviatedCommandLength = 55;
    MongoDBTracing mongoDBTracing = MongoDBTracing.newBuilder(tracing)
      .maxAbbreviatedCommandLength(maxAbbreviatedCommandLength)
      .clearCommandsWithCollectionName()
      .addCommandWithCollectionName("testCommand")
      .addAllCommandsWithCollectionName(Arrays.asList("command2", "command3"))
      .build();
    assertThat(mongoDBTracing).extracting("tracing").isEqualTo(tracing);
    assertThat(mongoDBTracing).extracting("maxAbbreviatedCommandLength").isEqualTo(maxAbbreviatedCommandLength);
    assertThat(mongoDBTracing).extracting("commandsWithCollectionName").asInstanceOf(InstanceOfAssertFactories.ITERABLE)
      .containsExactlyInAnyOrder("testCommand", "command2", "command3");
  }

  @Test public void toBuilder_setsValuesCorrectly() {
    int maxAbbreviatedCommandLength = 55;
    MongoDBTracing.Builder builder = MongoDBTracing.newBuilder(tracing)
      .maxAbbreviatedCommandLength(maxAbbreviatedCommandLength)
      .clearCommandsWithCollectionName()
      .addCommandWithCollectionName("testCommand")
      .build()
      .toBuilder();
    assertThat(builder).extracting("tracing").isEqualTo(tracing);
    assertThat(builder).extracting("maxAbbreviatedCommandLength").isEqualTo(maxAbbreviatedCommandLength);
    assertThat(builder).extracting("commandsWithCollectionName").asInstanceOf(InstanceOfAssertFactories.ITERABLE)
      .containsExactlyInAnyOrder("testCommand");
  }

  @Test public void commandListener_returnsTraceMongoCommandListener() {
    Tracer tracer = mock(Tracer.class);
    when(tracing.tracer()).thenReturn(tracer);
    int maxAbbreviatedCommandLength = 55;
    CommandListener listener = MongoDBTracing.newBuilder(tracing)
      .maxAbbreviatedCommandLength(maxAbbreviatedCommandLength)
      .clearCommandsWithCollectionName()
      .addCommandWithCollectionName("testCommand")
      .build()
      .commandListener();
    assertThat(listener).isInstanceOf(TraceMongoCommandListener.class);
    assertThat(listener).extracting("threadLocalSpan").extracting("tracer").isEqualTo(tracer);
    assertThat(listener).extracting("maxAbbreviatedCommandLength").isEqualTo(maxAbbreviatedCommandLength);
    assertThat(listener).extracting("commandsWithCollectionName").asInstanceOf(InstanceOfAssertFactories.ITERABLE)
      .containsExactlyInAnyOrder("testCommand");
  }
}
