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
package brave.http;

import brave.internal.Nullable;

/** @deprecated Intentionally hidden: implemented to support deprecated signatures. */
@Deprecated final class HttpClientAdapters {
  // Void type used to force generics to fail handling the wrong side.
  @Deprecated static final class ToRequestAdapter extends HttpClientAdapter<Object, Void> {
    final HttpClientRequest delegate;
    final Object unwrapped;

    ToRequestAdapter(HttpClientRequest delegate, Object unwrapped) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      if (unwrapped == null) throw new NullPointerException("unwrapped == null");
      this.delegate = delegate;
      this.unwrapped = unwrapped;
    }

    @Override public final long startTimestamp(Object request) {
      if (request == unwrapped) return delegate.startTimestamp();
      return 0L;
    }

    @Override public final String method(Object request) {
      if (request == unwrapped) return delegate.method();
      return null;
    }

    @Override public final String url(Object request) {
      if (request == unwrapped) return delegate.url();
      return null;
    }

    @Override public final String requestHeader(Object request, String name) {
      if (request == unwrapped) return delegate.header(name);
      return null;
    }

    @Override public final String path(Object request) {
      if (request == unwrapped) return delegate.path();
      return null;
    }

    @Override public final String toString() {
      return delegate.toString();
    }

    // Skip response adapter methods

    @Override public final String methodFromResponse(Void response) {
      return null;
    }

    @Override public final String route(Void response) {
      return null;
    }

    @Override public final int statusCodeAsInt(Void response) {
      return 0;
    }

    @Override @Nullable public final Integer statusCode(Void response) {
      return null;
    }

    @Override public final long finishTimestamp(Void response) {
      return 0L;
    }
  }

  @Deprecated static final class FromRequestAdapter<Req> extends HttpClientRequest {
    final HttpClientAdapter<Req, ?> adapter;
    final Req request;

    FromRequestAdapter(HttpClientAdapter<Req, ?> adapter, Req request) {
      if (adapter == null) throw new NullPointerException("adapter == null");
      this.adapter = adapter;
      if (request == null) throw new NullPointerException("request == null");
      this.request = request;
    }

    @Override public Object unwrap() {
      return request;
    }

    @Override public long startTimestamp() {
      return adapter.startTimestamp(request);
    }

    @Override public String method() {
      return adapter.method(request);
    }

    @Override public String path() {
      return adapter.path(request);
    }

    @Override public String url() {
      return adapter.url(request);
    }

    @Override public String header(String name) {
      return adapter.requestHeader(request, name);
    }

    @Override public void header(String name, String value) {
      // not called
    }

    @Override public final String toString() {
      return request.toString();
    }
  }

  // Void type used to force generics to fail handling the wrong side
  @Deprecated static final class ToResponseAdapter extends HttpClientAdapter<Void, Object> {
    final HttpClientResponse delegate;
    final Object unwrapped;

    ToResponseAdapter(HttpClientResponse delegate, Object unwrapped) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      if (unwrapped == null) throw new NullPointerException("unwrapped == null");
      this.delegate = delegate;
      this.unwrapped = unwrapped;
    }

    // Skip request adapter methods
    @Override public final String method(Void request) {
      return null;
    }

    @Override public final String path(Void request) {
      return null;
    }

    @Override public final String url(Void request) {
      return null;
    }

    @Override public final String requestHeader(Void request, String name) {
      return null;
    }

    @Override public final long startTimestamp(Void request) {
      return 0L;
    }

    // Begin response adapter methods

    @Override public final String methodFromResponse(Object response) {
      if (response == unwrapped) return delegate.method();
      return null;
    }

    @Override public final String route(Object response) {
      if (response == unwrapped) return delegate.route();
      return null;
    }

    @Override @Nullable public final Integer statusCode(Object response) {
      int result = statusCodeAsInt(response);
      return result == 0 ? null : result;
    }

    @Override public final int statusCodeAsInt(Object response) {
      if (response == unwrapped) return delegate.statusCode();
      return 0;
    }

    @Override public final long finishTimestamp(Object response) {
      if (response == unwrapped) return delegate.finishTimestamp();
      return 0L;
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  @Deprecated static final class FromResponseAdapter<Res> extends HttpClientResponse {
    final HttpClientAdapter<?, Res> adapter;
    final Res response;

    FromResponseAdapter(HttpClientAdapter<?, Res> adapter, Res response) {
      if (adapter == null) throw new NullPointerException("adapter == null");
      this.adapter = adapter;
      if (response == null) throw new NullPointerException("response == null");
      this.response = response;
    }

    @Override public Object unwrap() {
      return response;
    }

    @Override public String method() {
      return adapter.methodFromResponse(response);
    }

    @Override public String route() {
      return adapter.route(response);
    }

    @Override public int statusCode() {
      return adapter.statusCode(response);
    }

    @Override public long finishTimestamp() {
      return adapter.finishTimestamp(response);
    }

    @Override public String toString() {
      return response.toString();
    }
  }
}
