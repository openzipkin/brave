/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.Clock;
import brave.Response;
import brave.Span;
import brave.Tags;
import brave.internal.Nullable;
import brave.propagation.TraceContext;

/**
 * Abstract response type used for parsing and sampling of RPC clients and servers.
 *
 * @see RpcClientResponse
 * @see RpcServerResponse
 * @since 5.12
 */
public abstract class RpcResponse extends Response {
  /**
   * The request that initiated this RPC response or {@code null} if unknown.
   *
   * @since 5.12
   */
  @Override @Nullable public RpcRequest request() {
    return null;
  }

  /**
   * Returns the shortest human readable error code name. Ex. {code io.grpc.Status.Code.name()} may
   * return "CANCELLED".
   *
   * <p>Conventionally, this is used as the {@link Tags#ERROR "error" tag} and optionally tagged
   * separately as {@linkplain RpcTags#ERROR_CODE "rpc.error_code"}.
   *
   * <h3>Notes</h3>
   * This is not a success code. On success, return {@code null}. Do not return "OK" or similar as
   * it will interfere with error interpretation.
   *
   * <p>When only a boolean value is available, this should return empty string ("") on {@code
   * true} and {@code null} on false.
   *
   * <p>When an error code has both a numeric and text label, return the text.
   *
   * @since 5.12
   */
  @Nullable public abstract String errorCode();

  /**
   * The timestamp in epoch microseconds of the end of this request or zero to take this implicitly
   * from the current clock. Defaults to zero.
   *
   * <p>This is helpful in two scenarios: late parsing and avoiding redundant timestamp overhead.
   * For example, you can asynchronously handle span completion without losing precision of the
   * actual end.
   *
   * <p>Note: Overriding has the same problems as using {@link Span#finish(long)}. For
   * example, it can result in negative duration if the clock used is allowed to correct backwards.
   * It can also result in misalignments in the trace, unless {@link brave.Tracing.Builder#clock(Clock)}
   * uses the same implementation.
   *
   * @see RpcRequest#startTimestamp()
   * @see brave.Span#finish(long)
   * @see brave.Tracing#clock(TraceContext)
   * @since 5.12
   */
  public long finishTimestamp() {
    return 0L;
  }
}
