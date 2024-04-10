/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.dubbo;

public interface GreeterService {
  String sayHello(String name);

  String sayGoodbye(String name);
}
