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
package brave.internal.baggage;

import brave.baggage.BaggageField;
import brave.internal.Nullable;
import java.util.List;

public interface BaggageStateHandler<S> {
  List<BaggageField> currentFields(@Nullable S state);

  boolean handlesField(BaggageField field);

  @Nullable String getValue(BaggageField field, S state);

  S newState(BaggageField field, String value);

  S mergeState(S state, BaggageField field, @Nullable String value);

  @Nullable S decode(String encoded);

  String encode(S state);
}
