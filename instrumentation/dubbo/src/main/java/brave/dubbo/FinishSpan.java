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
package brave.dubbo;

import brave.Span;
import brave.internal.Nullable;
import brave.rpc.RpcClientHandler;
import brave.rpc.RpcClientRequest;
import brave.rpc.RpcServerHandler;
import java.util.function.BiConsumer;
import org.apache.dubbo.rpc.Result;

abstract class FinishSpan implements BiConsumer<Object, Throwable> {
  static void finish(TracingFilter filter,
    DubboRequest request, @Nullable Result result, @Nullable Throwable error, Span span) {
    if (request instanceof RpcClientRequest) {
      filter.clientHandler.handleReceive(
        new DubboClientResponse((DubboClientRequest) request, result, error), span);
    } else {
      filter.serverHandler.handleSend(
        new DubboServerResponse((DubboServerRequest) request, result, error), span);
    }
  }

  static FinishSpan create(TracingFilter filter, DubboRequest request, Result result, Span span) {
    if (request instanceof DubboClientRequest) {
      return new FinishClientSpan(
        span, result, filter.clientHandler, (DubboClientRequest) request);
    }
    return new FinishServerSpan(span, result, filter.serverHandler, (DubboServerRequest) request);
  }

  final Span span;
  final Result result;

  FinishSpan(Span span, Result result) {
    if (span == null) throw new NullPointerException("span == null");
    if (result == null) throw new NullPointerException("result == null");
    this.span = span;
    this.result = result;
  }

  static final class FinishClientSpan extends FinishSpan {
    final RpcClientHandler clientHandler;
    final DubboClientRequest request;

    FinishClientSpan(
      Span span, Result result, RpcClientHandler clientHandler, DubboClientRequest request) {
      super(span, result);
      this.clientHandler = clientHandler;
      this.request = request;
    }

    @Override public void accept(@Nullable Object unused, @Nullable Throwable error) {
      clientHandler.handleReceive(new DubboClientResponse(request, result, error), span);
    }
  }

  static final class FinishServerSpan extends FinishSpan {
    final RpcServerHandler serverHandler;
    final DubboServerRequest request;

    FinishServerSpan(
      Span span, Result result, RpcServerHandler serverHandler, DubboServerRequest request) {
      super(span, result);
      this.serverHandler = serverHandler;
      this.request = request;
    }

    @Override public void accept(@Nullable Object unused, @Nullable Throwable error) {
      serverHandler.handleSend(new DubboServerResponse(request, result, error), span);
    }
  }
}
