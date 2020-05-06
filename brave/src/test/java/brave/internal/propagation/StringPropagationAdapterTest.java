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
package brave.internal.propagation;

import brave.internal.propagation.StringPropagationAdapter.GetterAdapter;
import brave.internal.propagation.StringPropagationAdapter.SetterAdapter;
import brave.propagation.Propagation;
import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.KeyFactory;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StringPropagationAdapterTest {
  @Mock Propagation<String> delegate;
  @Mock Injector<Map<String, String>> delegateInjector;
  @Mock Extractor<Map<String, String>> delegateExtractor;

  KeyFactory<Integer> keyFactory = Integer::parseInt;
  Setter<Map<Integer, String>, Integer> setter = (m, k, v) -> m.put(k, v);
  Getter<Map<Integer, String>, Integer> getter = (m, k) -> m.get(k);

  @Test public void propagation() {
    when(delegate.keys()).thenReturn(asList("1", "2"));

    Propagation<Integer> propagation = StringPropagationAdapter.create(delegate, keyFactory);

    verify(delegate).keys();
    assertThat(propagation.keys()).containsExactly(1, 2);

    Map<String, Integer> map = new LinkedHashMap<>();
    map.put("1", 1);
    map.put("2", 2);

    assertThat(propagation)
        .asInstanceOf(InstanceOfAssertFactories.type(StringPropagationAdapter.class))
        .extracting(spa -> spa.map)
        .isEqualTo(map);

    when(delegate.injector(isA(SetterAdapter.class)))
        .thenReturn(delegateInjector);

    propagation.injector(setter);
    verify(delegate).injector(new SetterAdapter<>(setter, map));

    when(delegate.extractor(isA(GetterAdapter.class)))
        .thenReturn(delegateExtractor);

    propagation.extractor(getter);
    verify(delegate).extractor(new GetterAdapter<>(getter, map));
  }

  @Test public void getterAdapter() {
    Map<String, Integer> map = new LinkedHashMap<>();
    map.put("1", 1);
    map.put("2", 2);

    Getter<Map<Integer, String>, String> wrappedGetter = new GetterAdapter<>(getter, map);

    Map<Integer, String> request = new LinkedHashMap<>();
    request.put(1, "one");
    request.put(2, "two");

    assertThat(wrappedGetter.get(request, "1")).isEqualTo("one");
    assertThat(wrappedGetter.get(request, "2")).isEqualTo("two");
    assertThat(wrappedGetter.get(request, "3")).isNull();
  }

  @Test public void setterAdapter() {
    Map<String, Integer> map = new LinkedHashMap<>();
    map.put("1", 1);
    map.put("2", 2);

    Setter<Map<Integer, String>, String> wrappedSetter = new SetterAdapter<>(setter, map);

    Map<Integer, String> request = new LinkedHashMap<>();

    wrappedSetter.put(request, "1", "one");
    wrappedSetter.put(request, "2", "two");
    wrappedSetter.put(request, "3", "three");

    assertThat(request)
        .hasSize(2)
        .containsEntry(1, "one")
        .containsEntry(2, "two");
  }

  @Test public void propagation_equalsHashCodeString() {
    assertDelegates(
        () -> mock(Propagation.class),
        p -> StringPropagationAdapter.create(p, keyFactory)
    );
  }

  @Test public void getter_equalsHashCodeString() {
    assertDelegates(
        () -> mock(Getter.class),
        g -> new StringPropagationAdapter.GetterAdapter<>(g, Collections.emptyMap())
    );
  }

  @Test public void setter_equalsHashCodeString() {
    assertDelegates(
        () -> mock(Setter.class),
        g -> new SetterAdapter<>(g, Collections.emptyMap())
    );
  }

  <T> void assertDelegates(Supplier<T> factory, Function<T, T> wrapperFactory) {
    T delegate = factory.get(), wrapper = wrapperFactory.apply(delegate);

    assertThat(wrapper).isEqualTo(delegate);
    assertThat(wrapper).hasSameHashCodeAs(delegate);
    assertThat(wrapper).hasToString(delegate.toString());

    T sameDelegate = wrapperFactory.apply(delegate);
    assertThat(wrapper).isEqualTo(sameDelegate);
    assertThat(sameDelegate).isEqualTo(wrapper);
    assertThat(wrapper).hasSameHashCodeAs(sameDelegate);
    assertThat(wrapper).hasToString(delegate.toString());

    T different = wrapperFactory.apply(factory.get());
    assertThat(wrapper).isNotEqualTo(different);
    assertThat(different).isNotEqualTo(wrapper);
    assertThat(wrapper.hashCode()).isNotEqualTo(different.hashCode());
    assertThat(wrapper.toString()).isNotEqualTo(different.toString());
  }
}
