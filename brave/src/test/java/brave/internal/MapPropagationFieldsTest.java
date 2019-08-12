/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.internal;

import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class MapPropagationFieldsTest extends PropagationFieldsFactoryTest<MapPropagationFields> {
  @Override protected PropagationFieldsFactory<MapPropagationFields> newFactory() {
    return new PropagationFieldsFactory<MapPropagationFields>() {
      @Override public Class<MapPropagationFields> type() {
        return MapPropagationFields.class;
      }

      @Override public MapPropagationFields create() {
        return new MapPropagationFields();
      }

      @Override protected MapPropagationFields create(MapPropagationFields parent) {
        return new MapPropagationFields(parent);
      }
    };
  }

  @Test public void put_allows_arbitrary_field() {
    MapPropagationFields fields = factory.create();

    fields.put("balloon-color", "red");

    assertThat(fields.values)
      .containsEntry("balloon-color", "red");
  }

  @Test public void put_idempotent() {
    MapPropagationFields fields = factory.create();

    fields.put("balloon-color", "red");
    Map<String, String> fieldsMap = fields.values;

    fields.put("balloon-color", "red");
    assertThat(fields.values)
      .isSameAs(fieldsMap);

    fields.put("balloon-color", "blue");
    assertThat(fields.values)
      .isNotSameAs(fieldsMap);
  }

  @Test public void unmodifiable() {
    MapPropagationFields fields = factory.create();

    fields.put(FIELD1, "a");

    try {
      fields.values.put(FIELD1, "b");
      failBecauseExceptionWasNotThrown(UnsupportedOperationException.class);
    } catch (UnsupportedOperationException e) {
    }
  }
}
