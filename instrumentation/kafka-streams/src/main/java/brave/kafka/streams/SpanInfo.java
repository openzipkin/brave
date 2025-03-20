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
package brave.kafka.streams;

import java.util.Collections;
import java.util.Map;

public class SpanInfo {
  public final String spanName;
  public final Map<Long, String> annotations;
  public final Map<String, String> tags;

  public SpanInfo(String spanName, Map<Long, String> annotations, Map<String, String> tags) {
    this.spanName = spanName;
    this.annotations = annotations;
    this.tags = tags;
  }

  public SpanInfo(String spanName) {
    this.spanName = spanName;
    this.annotations = Collections.emptyMap();
    this.tags = Collections.emptyMap();
  }
}
