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

import brave.ScopedSpan;
import brave.Span;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import java.util.List;

/**
 * Escalate internal APIs in {@code brave.propagation} so they can be used from outside packages.
 * The only implementation is in {@link SamplingFlags}.
 *
 * <p>Inspired by {@code okhttp3.internal.Internal}.
 */
public abstract class InternalPropagation {
  /**
   * A flags bitfield is used internally inside {@link TraceContext} as opposed to several booleans.
   * This reduces the size of the object and allows us to set or check a couple states at once.
   */
  public static final int FLAG_SAMPLED = 1 << 1;
  public static final int FLAG_SAMPLED_SET = 1 << 2;
  public static final int FLAG_DEBUG = 1 << 3;
  public static final int FLAG_SHARED = 1 << 4;
  public static final int FLAG_SAMPLED_LOCAL = 1 << 5;
  public static final int FLAG_LOCAL_ROOT = 1 << 6;

  public static InternalPropagation instance;

  public abstract int flags(SamplingFlags flags);

  public static int sampled(boolean sampled, int flags) {
    if (sampled) {
      flags |= FLAG_SAMPLED | FLAG_SAMPLED_SET;
    } else {
      flags |= FLAG_SAMPLED_SET;
      flags &= ~FLAG_SAMPLED;
    }
    return flags;
  }

  /**
   * @param localRootId must be non-zero prior to instantiating {@link Span} or {@link ScopedSpan}
   */
  public abstract TraceContext newTraceContext(
    int flags,
    long traceIdHigh,
    long traceId,
    long localRootId,
    long parentId,
    long spanId,
    List<Object> extra
  );

  /** {@linkplain brave.propagation.TraceContext} is immutable so you need to read the result */
  public abstract TraceContext withExtra(TraceContext context, List<Object> immutableExtra);

  /** {@linkplain brave.propagation.TraceContext} is immutable so you need to read the result */
  public abstract TraceContext withFlags(TraceContext context, int flags);
}
