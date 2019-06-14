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
package brave.jms;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicates a type or method is only available since JMS 2.0, mainly to be careful so we don't
 * break JMS 1.1.
 *
 * <p>For example, a wrapped method on a type present in JMS 1.1, but defined in JMS 2.0, should
 * not use {@linkplain Override}.
 */
@java.lang.annotation.Documented
@java.lang.annotation.Retention(RetentionPolicy.SOURCE)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD}) @interface JMS2_0 {
}