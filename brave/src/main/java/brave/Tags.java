/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
      if (input == null) throw new NullPointerException("input == null");
      String message = input.getMessage();
      if (message != null) return message;
      if (input.getClass().isAnonymousClass()) { // avoids ""
        return input.getClass().getSuperclass().getSimpleName();
      }
      return input.getClass().getSimpleName();
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
