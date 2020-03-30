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

import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.TraceContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

public class CorrelationFieldScopeDecoratorBuilderTest {
  static final Map<String, String> ctx = new LinkedHashMap<>();

  ExtraFieldPropagation.Factory extraFactory =
    ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "user-id");

  TraceContext context = extraFactory.decorate(TraceContext.newBuilder()
    .traceId(1L)
    .parentId(2L)
    .spanId(3L)
    .sampled(true)
    .build());

  ScopeDecorator decorator = new TestBuilder(ctx).build();
  ScopeDecorator onlyTraceIdDecorator = new TestBuilder(ctx)
    .removeField("parentId")
    .removeField("spanId")
    .removeField("sampled")
    .build();
  ScopeDecorator onlyExtraFieldDecorator = new TestBuilder(ctx)
    .removeField("traceId")
    .removeField("parentId")
    .removeField("spanId")
    .removeField("sampled")
    .addExtraField("user-id")
    .build();

  ScopeDecorator withExtraFieldDecorator = new TestBuilder(ctx)
    .addExtraField("user-id")
    .build();

  @Before public void before() {
    ctx.clear();
    ExtraFieldPropagation.set(context, "user-id", "romeo");
  }

  @Test public void doesntDecorateNoop() {
    assertThat(decorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(onlyExtraFieldDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(withExtraFieldDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(onlyExtraFieldDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
  }

  @Test public void addsAndRemoves() {
    Scope decorated = decorator.decorateScope(context, mock(Scope.class));
    assertThat(ctx).containsExactly(
      entry("traceId", "0000000000000001"),
      entry("parentId", "0000000000000002"),
      entry("spanId", "0000000000000003"),
      entry("sampled", "true")
    );
    decorated.close();
    assertThat(ctx.isEmpty());
  }

  @Test public void addsAndRemoves_onlyTraceId() {
    Scope decorated = onlyTraceIdDecorator.decorateScope(context, mock(Scope.class));
    assertThat(ctx).containsExactly(entry("traceId", "0000000000000001"));
    decorated.close();
    assertThat(ctx.isEmpty());
  }

  @Test public void addsAndRemoves_onlyExtraField() {
    Scope decorated = onlyExtraFieldDecorator.decorateScope(context, mock(Scope.class));
    assertThat(ctx).containsExactly(entry("user-id", "romeo"));
    decorated.close();
    assertThat(ctx.isEmpty());
  }

  @Test public void addsAndRemoves_withExtraField() {
    Scope decorated = withExtraFieldDecorator.decorateScope(context, mock(Scope.class));
    assertThat(ctx).containsExactly(
      entry("traceId", "0000000000000001"),
      entry("parentId", "0000000000000002"),
      entry("spanId", "0000000000000003"),
      entry("sampled", "true"),
      entry("user-id", "romeo")
    );
    decorated.close();
    assertThat(ctx.isEmpty());
  }

  @Test public void revertsChanges() {
    ctx.put("traceId", "000000000000000a");
    ctx.put("parentId", "000000000000000b");
    ctx.put("spanId", "000000000000000c");
    ctx.put("sampled", "false");

    Scope decorated = decorator.decorateScope(context, mock(Scope.class));
    assertThat(ctx).containsExactly(
      entry("traceId", "0000000000000001"),
      entry("parentId", "0000000000000002"),
      entry("spanId", "0000000000000003"),
      entry("sampled", "true")
    );
    decorated.close();

    assertThat(ctx).containsExactly(
      entry("traceId", "000000000000000a"),
      entry("parentId", "000000000000000b"),
      entry("spanId", "000000000000000c"),
      entry("sampled", "false")
    );
  }

  @Test public void revertsChanges_onlyTraceId() {
    ctx.put("traceId", "000000000000000a");

    Scope decorated = onlyTraceIdDecorator.decorateScope(context, mock(Scope.class));
    assertThat(ctx).containsExactly(entry("traceId", "0000000000000001"));
    decorated.close();

    assertThat(ctx).containsExactly(entry("traceId", "000000000000000a"));
  }

  @Test public void revertsChanges_onlyExtraField() {
    ctx.put("user-id", "bob");

    Scope decorated = onlyExtraFieldDecorator.decorateScope(context, mock(Scope.class));
    assertThat(ctx).containsExactly(entry("user-id", "romeo"));
    decorated.close();

    assertThat(ctx).containsExactly(entry("user-id", "bob"));
  }

  @Test public void revertsChanges_withExtraField() {
    ctx.put("traceId", "000000000000000a");
    ctx.put("parentId", "000000000000000b");
    ctx.put("spanId", "000000000000000c");
    ctx.put("sampled", "false");
    ctx.put("user-id", "bob");

    Scope decorated = withExtraFieldDecorator.decorateScope(context, mock(Scope.class));
    assertThat(ctx).containsExactly(
      entry("traceId", "0000000000000001"),
      entry("parentId", "0000000000000002"),
      entry("spanId", "0000000000000003"),
      entry("sampled", "true"),
      entry("user-id", "romeo")
    );
    decorated.close();

    assertThat(ctx).containsExactly(
      entry("traceId", "000000000000000a"),
      entry("parentId", "000000000000000b"),
      entry("spanId", "000000000000000c"),
      entry("sampled", "false"),
      entry("user-id", "bob")
    );
  }

  @Test public void revertsLateChanges() {
    Scope decorated = decorator.decorateScope(context, mock(Scope.class));
    assertThat(ctx).containsExactly(
      entry("traceId", "0000000000000001"),
      entry("parentId", "0000000000000002"),
      entry("spanId", "0000000000000003"),
      entry("sampled", "true")
    );

    // late changes
    ctx.put("traceId", "000000000000000a");
    ctx.put("parentId", "000000000000000b");
    ctx.put("spanId", "000000000000000c");
    ctx.put("sampled", "false");

    decorated.close();

    assertThat(ctx).isEmpty();
  }

  @Test public void revertsLateChanges_onlyTraceId() {
    Scope decorated = onlyTraceIdDecorator.decorateScope(context, mock(Scope.class));
    assertThat(ctx).containsExactly(entry("traceId", "0000000000000001"));

    // late changes
    ctx.put("traceId", "000000000000000a");

    decorated.close();

    assertThat(ctx).isEmpty();
  }

  @Test public void revertsLateChanges_onlyExtraField() {
    Scope decorated = onlyExtraFieldDecorator.decorateScope(context, mock(Scope.class));
    assertThat(ctx).containsExactly(entry("user-id", "romeo"));

    // late changes
    ctx.put("user-id", "bob");

    decorated.close();

    assertThat(ctx).isEmpty();
  }

  @Test public void revertsLateChanges_withExtraField() {
    Scope decorated = withExtraFieldDecorator.decorateScope(context, mock(Scope.class));
    assertThat(ctx).containsExactly(
      entry("traceId", "0000000000000001"),
      entry("parentId", "0000000000000002"),
      entry("spanId", "0000000000000003"),
      entry("sampled", "true"),
      entry("user-id", "romeo")
    );

    // late changes
    ctx.put("traceId", "000000000000000a");
    ctx.put("parentId", "000000000000000b");
    ctx.put("spanId", "000000000000000c");
    ctx.put("sampled", "false");
    ctx.put("user-id", "bob");

    decorated.close();

    assertThat(ctx).isEmpty();
  }

  static class TestBuilder extends CorrelationFieldScopeDecoratorBuilder<TestBuilder> {
    TestBuilder(Map<String, String> context) {
      super(new Context() {
        @Override public String get(String name) {
          return context.get(name);
        }

        @Override public void put(String name, String value) {
          context.put(name, value);
        }

        @Override public void remove(String name) {
          context.remove(name);
        }
      });
    }
  }
}
