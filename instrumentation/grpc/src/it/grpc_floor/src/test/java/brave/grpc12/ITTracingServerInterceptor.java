/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.grpc_floor;

import brave.grpc.BaseITTracingServerInterceptor;
import io.grpc.ManagedChannelBuilder;

class ITTracingServerInterceptor extends BaseITTracingServerInterceptor {
  @Override protected ManagedChannelBuilder<?> usePlainText(ManagedChannelBuilder<?> builder) {
    return builder.usePlaintext(true);
  }
}
