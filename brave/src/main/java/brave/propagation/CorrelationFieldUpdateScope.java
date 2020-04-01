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
package brave.propagation;

import brave.internal.CorrelationContext;
import brave.internal.Nullable;
import brave.propagation.CorrelationField.Updatable;
import brave.propagation.CurrentTraceContext.Scope;
import java.util.concurrent.atomic.AtomicBoolean;

import static brave.propagation.CorrelationScopeDecorator.equal;
import static brave.propagation.CorrelationScopeDecorator.isSet;
import static brave.propagation.CorrelationScopeDecorator.setBit;
import static brave.propagation.CorrelationScopeDecorator.update;

/** Handles reverting potentially late value updates to correlation fields. */
abstract class CorrelationFieldUpdateScope extends AtomicBoolean implements Scope {
  CorrelationContext context;

  CorrelationFieldUpdateScope(CorrelationContext context) {
    this.context = context;
  }

  /**
   * Called after a field value is flushed to the underlying context. Only take action if the input
   * field is current being tracked.
   */
  abstract void handleUpdate(Updatable field, @Nullable String value);

  static final class Single extends CorrelationFieldUpdateScope {
    final Scope delegate;
    final CorrelationField trackedField;
    final @Nullable String valueToRevert;
    boolean dirty;

    Single(
      Scope delegate,
      CorrelationContext context,
      CorrelationField trackedField,
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
      if (dirty) update(context, trackedField, valueToRevert);
    }

    @Override void handleUpdate(Updatable field, String value) {
      if (!this.trackedField.equals(field)) return;
      if (!equal(value, valueToRevert)) dirty = true;
    }
  }

  static final class Multiple extends CorrelationFieldUpdateScope {
    final Scope delegate;
    final CorrelationField[] fields;
    final String[] valuesToRevert;
    int dirty;

    Multiple(
      Scope delegate,
      CorrelationContext context,
      CorrelationField[] fields,
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
        if (isSet(dirty, i)) update(context, fields[i], valuesToRevert[i]);
      }
    }

    @Override void handleUpdate(Updatable field, String value) {
      for (int i = 0; i < fields.length; i++) {
        if (fields[i].equals(field)) {
          if (!equal(value, valuesToRevert[i])) dirty = setBit(dirty, i);
          return;
        }
      }
    }
  }
}
