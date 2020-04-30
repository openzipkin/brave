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
package brave.baggage;

import brave.internal.baggage.BaggageCodec;
import brave.internal.baggage.SingleFieldBaggageCodec;
import brave.propagation.Propagation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static brave.baggage.BaggageField.validateName;

/**
 * Holds {@link BaggagePropagation} configuration.
 *
 * <h3>Field mapping</h3>
 * Your log correlation properties may not be the same as the baggage field names. You can override
 * them with the builder as needed.
 *
 * <p>Ex. If your log property is %X{trace-id}, you can do this:
 * <pre>{@code
 * import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
 *
 * scopeBuilder.clear() // TRACE_ID is a default field!
 *             .add(SingleBaggageField.newBuilder(BaggageFields.TRACE_ID)
 *                                        .name("trace-id").build())
 * }</pre>
 *
 * <p><em>Note</em>At the moment, dynamic fields are not supported. Use {@link
 * SingleBaggageField} for each field you need to propagate.
 *
 * @see BaggagePropagation
 * @see BaggageField
 * @see BaggagePropagationConfig
 * @see BaggagePropagationCustomizer
 * @since 5.11
 */
public class BaggagePropagationConfig {
  /**
   * Holds {@link BaggagePropagation} configuration for a {@linkplain BaggageField baggage field}.
   *
   * @see BaggagePropagation
   * @see BaggageField
   * @since 5.11
   */
  public static class SingleBaggageField extends BaggagePropagationConfig {
    /**
     * Configures this field for only local propagation. This will not be read from or written to
     * remote headers.
     *
     * @see #remote(BaggageField)
     * @since 5.11
     */
    public static SingleBaggageField local(BaggageField field) {
      return new Builder(field).build();
    }

    /**
     * Configures this field for remote propagation using its lower-case {@link BaggageField#name()}
     * as the only {@linkplain #keyNames() propagation key name}.
     *
     * @see #local(BaggageField)
     * @see #newBuilder(BaggageField) to use different propagation key names.
     */
    public static SingleBaggageField remote(BaggageField field) {
      return new Builder(field).addKeyName(field.lcName).build();
    }

    /** @since 5.11 */
    public static Builder newBuilder(BaggageField field) {
      return new Builder(field);
    }

    /**
     * Allows decorators to reconfigure correlation of this {@link #field()}
     *
     * @see BaggagePropagationCustomizer
     * @since 5.11
     */
    public Builder toBuilder() {
      return new Builder(this);
    }

    /** @since 5.11 */
    public static final class Builder {
      final BaggageField field;
      List<String> keyNames = new ArrayList<>();

      Builder(BaggageField field) {
        this.field = field;
      }

      Builder(SingleBaggageField input) {
        this.field = input.field;
        this.keyNames = new ArrayList<>(input.keyNames());
      }

      /**
       * Configures a {@linkplain Propagation#keys() key name} for remote propagation.
       *
       * @see SingleBaggageField#keyNames()
       * @since 5.11
       */
      public Builder addKeyName(String keyName) {
        if (keyName == null) throw new NullPointerException("keyName == null");
        String lcName = validateName(keyName).toLowerCase(Locale.ROOT);
        if (!keyNames.contains(lcName)) keyNames.add(lcName);
        return this;
      }

      /** @since 5.11 */
      public SingleBaggageField build() {
        return new SingleBaggageField(this);
      }
    }

    final BaggageField field;
    final Set<String> keyNames;

    SingleBaggageField(Builder builder) { // sealed to this package
      super(builder.keyNames.isEmpty()
          ? BaggageCodec.NOOP
          : SingleFieldBaggageCodec.single(builder.field, builder.keyNames));
      field = builder.field;
      keyNames = builder.keyNames.isEmpty() ? Collections.emptySet()
          : Collections.unmodifiableSet(new LinkedHashSet<>(builder.keyNames));
    }

    public BaggageField field() {
      return field;
    }

    /**
     * Returns a possibly empty list of lower-case {@link Propagation#keys() propagation key names}.
     * When empty, the field is not propagated remotely.
     *
     * @since 5.11
     */
    public Set<String> keyNames() {
      return keyNames;
    }
  }

  /** Returns {@link BaggageCodec#NOOP} if {@link SingleBaggageField#local(BaggageField)}. */
  final BaggageCodec baggageCodec;

  BaggagePropagationConfig(BaggageCodec baggageCodec) {
    if (baggageCodec == null) throw new NullPointerException("baggageCodec == null");
    this.baggageCodec = baggageCodec;
  }
}
