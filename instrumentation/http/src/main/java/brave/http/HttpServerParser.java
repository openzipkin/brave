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
package brave.http;

import brave.SpanCustomizer;
import brave.internal.Nullable;

/**
 * Parses the request and response into reasonable defaults for http server spans. Subclass to
 * customize, for example, to add tags based on user ID.
 */
public class HttpServerParser extends HttpParser {

  /**
   * Customizes the span based on the request received from the client.
   *
   * <p>{@inheritDoc}
   */
  @Override public <Req> void request(HttpAdapter<Req, ?> adapter, Req req,
    SpanCustomizer customizer) {
    super.request(adapter, req, customizer);
  }

  /**
   * Customizes the span based on the response sent to the client.
   *
   * <p>{@inheritDoc}
   */
  @Override public <Resp> void response(HttpAdapter<?, Resp> adapter, @Nullable Resp res,
    @Nullable Throwable error, SpanCustomizer customizer) {
    super.response(adapter, res, error, customizer);
  }
}
