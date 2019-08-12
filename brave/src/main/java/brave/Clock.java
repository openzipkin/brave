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
package brave;

/**
 * Epoch microseconds used for {@link zipkin2.Span#timestamp()} and {@link
 * zipkin2.Annotation#timestamp()}.
 *
 * <p>This should use the most precise value possible. For example, {@code gettimeofday} or
 * multiplying {@link System#currentTimeMillis} by 1000.
 *
 * <p>See <a href="https://zipkin.io/pages/instrumenting.html">Instrumenting a service</a> for
 * more.
 */
// FunctionalInterface except Java language level 6
public interface Clock {

  long currentTimeMicroseconds();
}
