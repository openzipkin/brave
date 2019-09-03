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
package brave.test.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.util.log.AbstractLogger;

final class Log4J2Log extends AbstractLogger {
  final Logger logger;

  Log4J2Log() {
    this("org.eclipse.jetty.util.log");
  }

  Log4J2Log(String name) {
    this.logger = LogManager.getLogger(name);
  }

  @Override public String getName() {
    return logger.getName();
  }

  @Override public void warn(String msg, Object... args) {
    logger.warn(msg, args);
  }

  @Override public void warn(Throwable thrown) {
    warn("", thrown);
  }

  @Override public void warn(String msg, Throwable thrown) {
    logger.warn(msg, thrown);
  }

  @Override public void info(String msg, Object... args) {
    logger.info(msg, args);
  }

  @Override public void info(Throwable thrown) {
    this.info("", thrown);
  }

  @Override public void info(String msg, Throwable thrown) {
    logger.info(msg, thrown);
  }

  @Override public void debug(String msg, Object... args) {
    logger.debug(msg, args);
  }

  @Override public void debug(Throwable thrown) {
    this.debug("", thrown);
  }

  @Override public void debug(String msg, Throwable thrown) {
    logger.debug(msg, thrown);
  }

  @Override public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  @Override public void setDebugEnabled(boolean enabled) {
    this.warn("setDebugEnabled not implemented");
  }

  @Override protected org.eclipse.jetty.util.log.Logger newLogger(String fullname) {
    return new Log4J2Log(fullname);
  }

  @Override public void ignore(Throwable ignored) {
  }

  @Override public String toString() {
    return logger.toString();
  }
}
