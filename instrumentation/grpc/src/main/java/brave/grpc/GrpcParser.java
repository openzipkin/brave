/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.grpc;

import brave.internal.Nullable;

final class GrpcParser  {
  @Nullable static String method(String fullMethodName) {
    int index = fullMethodName.lastIndexOf('/');
    if (index == -1 || index == 0) return null;
    return fullMethodName.substring(index + 1);
  }

  @Nullable static String service(String fullMethodName) {
    int index = fullMethodName.lastIndexOf('/');
    if (index == -1 || index == 0) return null;
    return fullMethodName.substring(0, index);
  }
}
