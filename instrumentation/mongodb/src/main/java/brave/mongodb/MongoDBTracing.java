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
package brave.mongodb;

import brave.Tracing;
import com.mongodb.event.CommandListener;

/**
 * Use this class to decorate your MongoDB client and enable Tracing.
 *
 * <p>To use it, call <code>.addCommandListener(MongoDBTracing.create(tracing).commandListener())</code>
 * on the {@link com.mongodb.MongoClientOptions} or {@link com.mongodb.MongoClientSettings} object
 * that is used to create the {@code MongoClient} to be instrumented.
 *
 * As of now, this instrumentation can only be used with the synchronous MongoDB driver. Do not use
 * it with the asynchronous or reactive drivers as tracing data will be incorrect.
 */
public final class MongoDBTracing {
  public static MongoDBTracing create(final Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static Builder newBuilder(final Tracing tracing) {
    return new Builder(tracing);
  }

  public CommandListener commandListener() {
    return new TraceMongoCommandListener(this);
  }

  public static final class Builder {
    final Tracing tracing;

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
    }

    public MongoDBTracing build() {
      return new MongoDBTracing(this);
    }
  }

  final Tracing tracing;

  MongoDBTracing(Builder builder) {
    tracing = builder.tracing;
  }
}
