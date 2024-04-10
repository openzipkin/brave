/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.mongodb;

import brave.Span;
import com.mongodb.ServerAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MongoDBDriverTest {
  @Mock ServerAddress serverAddress;
  @Mock Span span;

  @Test void setRemoteIpAndPort() {
    when(serverAddress.getHost()).thenReturn("127.0.0.1");
    when(serverAddress.getPort()).thenReturn(27017);

    MongoDBDriver.get().setRemoteIpAndPort(span, serverAddress);

    verify(span).remoteIpAndPort("127.0.0.1", 27017);
  }
}
