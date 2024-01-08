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

import java.io.OutputStream;

public class DerbyUtils {

  //Get rid of the annoying derby.log file
  public static void disableLog() {
    System.setProperty("derby.stream.error.field", DerbyUtils.class.getName() + ".DEV_NULL");
  }

  public static final OutputStream DEV_NULL = new OutputStream() {
    public void write(int b) {
    }
  };
}
