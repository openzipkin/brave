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
package brave.p6spy;

import com.p6spy.engine.logging.P6LogLoadableOptions;
import com.p6spy.engine.logging.P6LogOptions;
import com.p6spy.engine.spy.P6SpyOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;
import java.util.LinkedHashMap;
import java.util.Map;

final class TracingP6SpyOptions extends P6SpyOptions {

  static final String REMOTE_SERVICE_NAME = "remoteServiceName";
  static final String INCLUDE_PARAMETER_VALUES = "includeParameterValues";

  private final P6OptionsRepository optionsRepository;
  private final P6LogLoadableOptions logLoadableOptions;

  TracingP6SpyOptions(P6OptionsRepository optionsRepository) {
    super(optionsRepository);
    logLoadableOptions = new P6LogOptions(optionsRepository);
    this.optionsRepository = optionsRepository;
  }

  @Override
  public void load(Map<String, String> options) {
    super.load(options);
    logLoadableOptions.load(options);
    optionsRepository.set(String.class, REMOTE_SERVICE_NAME, options.get(REMOTE_SERVICE_NAME));
    optionsRepository.set(Boolean.class, INCLUDE_PARAMETER_VALUES,
      options.get(INCLUDE_PARAMETER_VALUES));
  }

  @Override
  public Map<String, String> getDefaults() {
    Map<String, String> allDefaults = new LinkedHashMap<>(super.getDefaults());
    allDefaults.putAll(logLoadableOptions.getDefaults());
    allDefaults.put(INCLUDE_PARAMETER_VALUES, Boolean.FALSE.toString());
    return allDefaults;
  }

  P6LogLoadableOptions getLogOptions() {
    return logLoadableOptions;
  }

  String remoteServiceName() {
    return optionsRepository.get(String.class, REMOTE_SERVICE_NAME);
  }

  Boolean includeParameterValues() {
    return optionsRepository.get(Boolean.class, INCLUDE_PARAMETER_VALUES);
  }
}
