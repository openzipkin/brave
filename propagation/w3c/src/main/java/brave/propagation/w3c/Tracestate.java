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
package brave.propagation.w3c;

import brave.internal.Nullable;

/**
 * This only contains other entries. The entry for the current trace is only written during
 * injection.
 */
final class Tracestate { // hidden intentionally
  static final Tracestate EMPTY = new Tracestate("");

  // TODO: this will change
  final String otherEntries;

  static Tracestate create(@Nullable CharSequence otherEntries) {
    if (otherEntries == null || otherEntries.length() == 0) return EMPTY;
    return new Tracestate(otherEntries.toString());
  }

  private Tracestate(String otherEntries) {
    this.otherEntries = otherEntries;
  }

  @Override public String toString() {
    return "tracestate: " + otherEntries;
  }

  @Override public final boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Tracestate)) return false;
    return otherEntries.equals(((Tracestate) o).otherEntries);
  }

  @Override public final int hashCode() {
    return otherEntries.hashCode();
  }
}