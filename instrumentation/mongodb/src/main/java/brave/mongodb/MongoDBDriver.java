/*
 * Copyright 2013-2024 The OpenZipkin Authors
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

import brave.Span;
import com.mongodb.ServerAddress;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;

import static brave.internal.Throwables.propagateIfFatal;

/**
 * Access to MongoDB version-specific features
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.platform.Platform}
 */
class MongoDBDriver {
  private static final MongoDBDriver MONGO_DB_DRIVER = findMongoDBDriver();

  /** adds the remote IP and port to the span */
  void setRemoteIpAndPort(Span span, ServerAddress address) {
    // default to no-op instead of crash on future drift
  }

  MongoDBDriver() {
  }

  public static MongoDBDriver get() {
    return MONGO_DB_DRIVER;
  }

  /**
   * Attempt to match the driver version from two known patterns:
   *
   * <ol>
   *   <li>org.mongodb:mongodb-driver - 3.x</li>
   *   <li>org.mongodb:mongodb-driver-core - 5.x</li>
   * </ol>
   */
  private static MongoDBDriver findMongoDBDriver() {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try {
      MethodHandle getHost =
        lookup.findVirtual(ServerAddress.class, "getHost", MethodType.methodType(String.class));
      MethodHandle getPort =
        lookup.findVirtual(ServerAddress.class, "getPort", MethodType.methodType(int.class));
      return new Driver5x(getHost, getPort);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // not 5.x
    }
    try {
      MethodHandle getSocketAddress = lookup.findVirtual(ServerAddress.class, "getSocketAddress",
        MethodType.methodType(InetSocketAddress.class));
      return new Driver3x(getSocketAddress);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // unknown version
    }

    // Unknown
    return new MongoDBDriver();
  }

  static final class Driver3x extends MongoDBDriver {
    final MethodHandle getSocketAddress;

    Driver3x(MethodHandle getSocketAddress) {
      this.getSocketAddress = getSocketAddress;
    }

    @Override void setRemoteIpAndPort(Span span, ServerAddress serverAddress) {
      try {
        InetSocketAddress socketAddress =
          (InetSocketAddress) getSocketAddress.invokeExact(serverAddress);
        span.remoteIpAndPort(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
      } catch (Throwable t) {
        propagateIfFatal(t);
      }
    }
  }

  static final class Driver5x extends MongoDBDriver {
    final MethodHandle getHost, getPort;

    Driver5x(MethodHandle getHost, MethodHandle getPort) {
      this.getHost = getHost;
      this.getPort = getPort;
    }

    @Override void setRemoteIpAndPort(Span span, ServerAddress serverAddress) {
      try {
        String host = (String) getHost.invokeExact(serverAddress);
        int port = (int) getPort.invokeExact(serverAddress);
        span.remoteIpAndPort(host, port);
      } catch (Throwable t) {
        propagateIfFatal(t);
      }
    }
  }
}
