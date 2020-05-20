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
