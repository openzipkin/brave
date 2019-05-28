/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brave;

import brave.handler.MutableSpan;
import brave.propagation.TraceContext;

/** This wraps the public api and guards access to a mutable span. */
final class RealSpanCustomizer implements SpanCustomizer {

  final TraceContext context;
  final MutableSpan state;
  final Clock clock;

  RealSpanCustomizer(TraceContext context, MutableSpan state, Clock clock) {
    this.context = context;
    this.state = state;
    this.clock = clock;
  }

  @Override public SpanCustomizer name(String name) {
    synchronized (state) {
      state.name(name);
    }
    return this;
  }

  @Override public SpanCustomizer annotate(String value) {
    long timestamp = clock.currentTimeMicroseconds();
    synchronized (state) {
      state.annotate(timestamp, value);
    }
    return this;
  }

  @Override public SpanCustomizer tag(String key, String value) {
    synchronized (state) {
      state.tag(key, value);
    }
    return this;
  }

  @Override
  public String toString() {
    return "RealSpanCustomizer(" + context + ")";
  }
}
