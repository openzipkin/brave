/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.extra;

final class BasicMapExtra extends MapExtra<String, String, BasicMapExtra, BasicMapExtra.Factory> {
  static final class FactoryBuilder
      extends MapExtraFactory.Builder<String, String, BasicMapExtra, Factory, FactoryBuilder> {
    @Override protected Factory build() {
      return new Factory(this);
    }
  }

  static final class Factory extends MapExtraFactory<String, String, BasicMapExtra, Factory> {
    Factory(FactoryBuilder builder) {
      super(builder);
    }

    @Override protected BasicMapExtra create() {
      return new BasicMapExtra(this);
    }
  }

  BasicMapExtra(Factory factory) {
    super(factory);
  }
}
