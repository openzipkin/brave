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
