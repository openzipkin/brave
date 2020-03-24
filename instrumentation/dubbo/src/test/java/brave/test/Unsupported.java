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
package brave.test;

import zipkin2.Span;

/** This avoids exposing edge-cases only for Dubbo. */
public abstract class Unsupported {

  /**
   * One-way RPC modeling is a rarely used. It pre-dated the messaging spans, and in hind sight,
   * better as a tag. It should be considered a deprecated practice. This method only exists to
   * support Dubbo, exposed to avoid exposing the raw span queue protected.
   */
  public static Span takeOneWayRpcSpan(ITRemote itRemote, Span.Kind kind) {
    Span span = itRemote.reporter.doTakeSpan(null, true);
    itRemote.reporter.assertRemoteSpan(span, kind);
    return span;
  }
}
