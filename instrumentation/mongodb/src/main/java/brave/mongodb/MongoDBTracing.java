/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
