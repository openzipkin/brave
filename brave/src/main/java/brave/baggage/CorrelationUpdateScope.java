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
package brave.baggage;

import brave.internal.CorrelationContext;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext.Scope;
import java.util.concurrent.atomic.AtomicBoolean;

import static brave.baggage.CorrelationScopeDecorator.equal;
import static brave.baggage.CorrelationScopeDecorator.isSet;
import static brave.baggage.CorrelationScopeDecorator.setBit;

/** Handles reverting potentially late value updates to baggage fields. */
abstract class CorrelationUpdateScope extends AtomicBoolean implements Scope {
  CorrelationContext context;

  CorrelationUpdateScope(CorrelationContext context) {
    this.context = context;
  }

  /**
   * Called to get the name of the field, before it is flushed to the underlying context.
   *
   * @return the name used for this field, or null if not configured.
   */
  @Nullable abstract String name(BaggageField field);

  /**
   * Called after a field value is flushed to the underlying context. Only take action if the input
   * field is current being tracked.
   */
  abstract void handleUpdate(BaggageField field, @Nullable String value);

  static final class Single extends CorrelationUpdateScope {
    final Scope delegate;
    final BaggageField field;
    final String name;
    final @Nullable String valueToRevert;
    boolean dirty;

    Single(
      Scope delegate,
      CorrelationContext context,
      BaggageField field,
      String name,
      @Nullable String valueToRevert,
      boolean dirty
    ) {
      super(context);
      this.delegate = delegate;
      this.field = field;
      this.name = name;
      this.valueToRevert = valueToRevert;
      this.dirty = dirty;
    }

    @Override public void close() {
      // don't duplicate work if called multiple times.
      if (!compareAndSet(false, true)) return;
      delegate.close();
      if (dirty) context.update(name, valueToRevert);
    }

    @Override String name(BaggageField field) {
      return name;
    }

    @Override void handleUpdate(BaggageField field, String value) {
      if (!this.field.equals(field)) return;
      if (!equal(value, valueToRevert)) dirty = true;
    }
  }

  static final class Multiple extends CorrelationUpdateScope {
    final Scope delegate;
    final BaggageField[] fields;
    final String[] names;
    final String[] valuesToRevert;
    int dirty;

    Multiple(
      Scope delegate,
      CorrelationContext context,
      BaggageField[] fields,
      String[] names,
      String[] valuesToRevert,
      int dirty
    ) {
      super(context);
      this.delegate = delegate;
      this.fields = fields;
      this.names = names;
      this.valuesToRevert = valuesToRevert;
      this.dirty = dirty;
    }

    @Override public void close() {
      // don't duplicate work if called multiple times.
      if (!compareAndSet(false, true)) return;

      delegate.close();
      for (int i = 0; i < fields.length; i++) {
        if (isSet(dirty, i)) context.update(names[i], valuesToRevert[i]);
      }
    }

    @Override String name(BaggageField field) {
      for (int i = 0; i < fields.length; i++) {
        if (fields[i].equals(field)) {
          return names[i];
        }
      }
      return null;
    }

    @Override void handleUpdate(BaggageField field, String value) {
      for (int i = 0; i < fields.length; i++) {
        if (fields[i].equals(field)) {
          if (!equal(value, valuesToRevert[i])) dirty = setBit(dirty, i);
          return;
        }
      }
    }
  }
}
