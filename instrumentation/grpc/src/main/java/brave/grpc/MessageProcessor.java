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
package brave.grpc;

import brave.SpanCustomizer;

// only here to deal with deprecated methods.
abstract class MessageProcessor {
  static final MessageProcessor NOOP = new MessageProcessor() {
    @Override public void onMessageSent(Object message, SpanCustomizer span) {
    }

    @Override public void onMessageReceived(Object message, SpanCustomizer span) {
    }
  };

  <M> void onMessageSent(M message, SpanCustomizer span) {
  }

  <M> void onMessageReceived(M message, SpanCustomizer span) {
  }
}
