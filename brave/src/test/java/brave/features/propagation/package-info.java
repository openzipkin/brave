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
/**
 * Before, we had the same algorithm for encoding B3 copy pasted a couple times. We now have a type
 * {@link brave.propagation.Propagation} which supplies an implementation such as B3. This includes
 * common functions such as how to extract and inject based on map-like carriers.
 */
package brave.features.propagation;
