/*
 * Copyright 2013-2019 The OpenZipkin Authors
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

/**
 * Returns true if this rule matches the input parameters
 *
 * <p>Implement {@link #hashCode()} and {@link #equals(Object)} if you want to replace existing
 * rules by something besides object identity.
 *
 * @see Matchers
 * @since 5.8
 */
public interface Matcher<P> {
  boolean matches(P parameters);
}
