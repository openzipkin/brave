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
package brave.grpc;

import brave.grpc.GrpcPropagation.Tags;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static brave.grpc.GrpcPropagation.extractTags;
import static org.assertj.core.api.Assertions.assertThat;

public class TagsTest {

  @Test public void extractTags_movesMethodToParentField() {
    Map<String, String> extracted = new LinkedHashMap<>();
    extracted.put("method", "helloworld.Greeter/SayHello");

    Tags tags = extractTags(extracted);
    assertThat(tags.parentMethod)
      .isEqualTo("helloworld.Greeter/SayHello");
    assertThat(tags.get("method"))
      .isNull();
  }

  @Test public void inheritsParentMethod() {
    Map<String, String> extracted = new LinkedHashMap<>();
    extracted.put("method", "helloworld.Greeter/SayHello");
    Tags parent = extractTags(extracted);

    Tags child = new Tags(parent);
    assertThat(child.parentMethod)
      .isEqualTo(parent.parentMethod);
  }
}
