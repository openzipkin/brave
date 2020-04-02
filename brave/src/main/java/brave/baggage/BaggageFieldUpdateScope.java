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
abstract class BaggageFieldUpdateScope extends AtomicBoolean implements Scope {
  CorrelationContext context;

  BaggageFieldUpdateScope(CorrelationContext context) {
    this.context = context;
  }

  /**
   * Called after a field value is flushed to the underlying context. Only take action if the input
   * field is current being tracked.
   */
  abstract void handleUpdate(BaggageField field, @Nullable String value);

  static final class Single extends BaggageFieldUpdateScope {
    final Scope delegate;
    final BaggageField trackedField;
    final @Nullable String valueToRevert;
    boolean dirty;

    Single(
      Scope delegate,
      CorrelationContext context,
      BaggageField trackedField,
      @Nullable String valueToRevert,
      boolean dirty
    ) {
      super(context);
      this.delegate = delegate;
      this.trackedField = trackedField;
      this.valueToRevert = valueToRevert;
      this.dirty = dirty;
    }

    @Override public void close() {
      // don't duplicate work if called multiple times.
      if (!compareAndSet(false, true)) return;
      delegate.close();
      if (dirty) context.update(trackedField.name, valueToRevert);
    }

    @Override void handleUpdate(BaggageField field, String value) {
      if (!this.trackedField.equals(field)) return;
      if (!equal(value, valueToRevert)) dirty = true;
    }
  }

  static final class Multiple extends BaggageFieldUpdateScope {
    final Scope delegate;
    final BaggageField[] fields;
    final String[] valuesToRevert;
    int dirty;

    Multiple(
      Scope delegate,
      CorrelationContext context,
      BaggageField[] fields,
      String[] valuesToRevert,
      int dirty
    ) {
      super(context);
      this.delegate = delegate;
      this.fields = fields;
      this.valuesToRevert = valuesToRevert;
      this.dirty = dirty;
    }

    @Override public void close() {
      // don't duplicate work if called multiple times.
      if (!compareAndSet(false, true)) return;

      delegate.close();
      for (int i = 0; i < fields.length; i++) {
        if (isSet(dirty, i)) context.update(fields[i].name, valuesToRevert[i]);
      }
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
