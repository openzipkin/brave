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
package brave.internal;

import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MapPropagationFieldsTest
  extends PropagationFieldsFactoryTest<String, String, Extra, Factory> {

  public MapPropagationFieldsTest() {
    super("one", "two", "1", "2", "3");
  }

  @Override protected Factory newFactory() {
    return new Factory();
  }

  @Test public void put_allows_arbitrary_field() {
    MapPropagationFields<String, String> fields = factory.create();

    fields.put("balloon-color", "red");

    assertThat(fields.values)
      .containsEntry("balloon-color", "red");
  }

  @Test public void put_idempotent() {
    MapPropagationFields<String, String> fields = factory.create();

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
    MapPropagationFields<String, String> fields = factory.create();

    fields.put(keyOne, "a");

    assertThatThrownBy(() -> fields.values.put(keyOne, "b"))
      .isInstanceOf(UnsupportedOperationException.class);
  }
}

final class Factory extends PropagationFieldsFactory<String, String, Extra> {
  @Override public Class<Extra> type() {
    return Extra.class;
  }

  @Override public Extra create() {
    return new Extra();
  }

  @Override protected Extra create(Extra parent) {
    return new Extra(parent);
  }
}

final class Extra extends MapPropagationFields<String, String> {
  Extra() {
    super();
  }

  Extra(Extra parent) {
    super(parent);
  }
}
