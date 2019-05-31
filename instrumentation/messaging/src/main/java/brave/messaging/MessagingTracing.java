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
package brave.messaging;

import brave.Tracing;

public class MessagingTracing {
  public static MessagingTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static MessagingTracing.Builder newBuilder(Tracing tracing) {
    return new MessagingTracing.Builder(tracing);
  }

  final Tracing tracing;
  final MessagingParser parser;

  MessagingTracing(Builder builder) {
    this.tracing = builder.tracing;
    this.parser = builder.parser;
  }

  public Tracing tracing() {
    return tracing;
  }

  public MessagingParser parser() {
    return parser;
  }

  public static class Builder {
    final Tracing tracing;
    MessagingParser parser = new MessagingParser();

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
    }

    public Builder parser(MessagingParser parser) {
      if (parser == null) throw new NullPointerException("parser == null");
      this.parser = parser;
      return this;
    }

    public MessagingTracing build() {
      return new MessagingTracing(this);
    }
  }
}
