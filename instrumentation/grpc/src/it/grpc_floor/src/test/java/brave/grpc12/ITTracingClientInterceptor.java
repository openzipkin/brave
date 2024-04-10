/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.grpc_floor;

import brave.grpc.BaseITTracingClientInterceptor;
import io.grpc.ManagedChannelBuilder;

class ITTracingClientInterceptor extends BaseITTracingClientInterceptor {
  @Override protected ManagedChannelBuilder<?> usePlainText(ManagedChannelBuilder<?> builder) {
    return builder.usePlaintext(true);
  }
}
