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

import brave.propagation.B3SingleFormat;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;

import static brave.propagation.w3c.TraceContextPropagation.TRACEPARENT;
import static brave.propagation.w3c.TraceContextPropagation.TRACESTATE;
import static brave.propagation.w3c.TraceparentFormat.writeTraceparentFormat;

final class TraceContextInjector<R> implements Injector<R> {
  final Setter<R, String> setter;
  final String tracestateKey;

  TraceContextInjector(TraceContextPropagation propagation, Setter<R, String> setter) {
    this.setter = setter;
    this.tracestateKey = propagation.tracestateKey;
  }

  @Override public void inject(TraceContext context, R request) {
    setter.put(request, TRACEPARENT, writeTraceparentFormat(context));
    Tracestate tracestate = context.findExtra(Tracestate.class);
    tracestate.put(tracestateKey, B3SingleFormat.writeB3SingleFormat(context));
    setter.put(request, TRACESTATE, tracestate.stateString());
  }
}
