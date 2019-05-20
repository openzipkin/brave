/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brave.p6spy;

import com.p6spy.engine.spy.P6SpyOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;
import java.util.Map;

final class TracingP6SpyOptions extends P6SpyOptions {
  static final String REMOTE_SERVICE_NAME = "remoteServiceName";
  static final String INCLUDE_PARAMETER_VALUES = "includeParameterValues";

  final P6OptionsRepository optionsRepository;

  TracingP6SpyOptions(P6OptionsRepository optionsRepository) {
    super(optionsRepository);
    this.optionsRepository = optionsRepository;
  }

  @Override public void load(Map<String, String> options) {
    super.load(options);
    optionsRepository.set(String.class, REMOTE_SERVICE_NAME, options.get(REMOTE_SERVICE_NAME));
    optionsRepository.set(Boolean.class, INCLUDE_PARAMETER_VALUES, options.get(INCLUDE_PARAMETER_VALUES));
  }

  String remoteServiceName() {
    return optionsRepository.get(String.class, REMOTE_SERVICE_NAME);
  }

  Boolean includeParameterValues() {
    Boolean logParameterValues = optionsRepository.get(Boolean.class, INCLUDE_PARAMETER_VALUES);
    return logParameterValues == null ? false : logParameterValues;
  }
}
