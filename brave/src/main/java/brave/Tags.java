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
package brave;

import brave.baggage.BaggageField;
import brave.propagation.TraceContext;

/**
 * Standard tags used in parsers
 *
 * @since 5.11
 */
public final class Tags {
  /**
   * This tags "error" as the message or simple name of the throwable.
   *
   * <p><em>Note:</em>Conventionally, Zipkin handlers will not overwrite a tag named "error" if set
   * directly such as this.
   *
   * @see Span#error(Throwable)
   * @since 5.11
   */
  public static final Tag<Throwable> ERROR = new Tag<Throwable>("error") {
    @Override protected String parseValue(Throwable input, TraceContext context) {
      return ErrorParser.parse(input);
    }
  };

  /**
   * This tags the baggage value using {@link BaggageField#name()} as the key.
   *
   * @see BaggageField
   * @since 5.11
   */
  public static final Tag<BaggageField> BAGGAGE_FIELD = new Tag<BaggageField>("baggageField") {
    @Override protected String key(BaggageField input) {
      return input.name();
    }

    @Override protected String parseValue(BaggageField input, TraceContext context) {
      return input.getValue(context);
    }
  };

  Tags() {
  }
}
