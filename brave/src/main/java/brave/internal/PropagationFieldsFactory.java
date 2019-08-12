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

import brave.propagation.TraceContext;
import java.util.List;

public abstract class PropagationFieldsFactory<P extends PropagationFields> extends
  ExtraFactory<P> {

  @Override protected abstract P create();

  @Override protected P createExtraAndClaim(long traceId, long spanId) {
    P result = create();
    result.tryToClaim(traceId, spanId);
    return result;
  }

  @Override protected P createExtraAndClaim(P existing, long traceId, long spanId) {
    P result = create(existing);
    result.tryToClaim(traceId, spanId);
    return result;
  }

  @Override protected boolean tryToClaim(P existing, long traceId, long spanId) {
    return existing.tryToClaim(traceId, spanId);
  }

  @Override protected void consolidate(P existing, P consolidated) {
    consolidated.putAllIfAbsent(existing);
  }

  // TODO: this is internal. If we ever expose it otherwise, we should use Lists.ensureImmutable
  @Override protected TraceContext contextWithExtra(TraceContext context,
    List<Object> immutableExtra) {
    return InternalPropagation.instance.withExtra(context, immutableExtra);
  }
}
