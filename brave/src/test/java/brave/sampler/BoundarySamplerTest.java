/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
package brave.sampler;

import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.data.Percentage.withPercentage;

class BoundarySamplerTest extends SamplerTest {
  @Override Sampler newSampler(float probability) {
    return BoundarySampler.create(probability);
  }

  @Override Percentage expectedErrorProbability() {
    return withPercentage(10);
  }

  @Test void acceptsOneInTenThousandProbability() {
    newSampler(0.0001f);
  }
}
