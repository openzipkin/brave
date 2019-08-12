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
package brave.mysql8;

import brave.Span;
import brave.propagation.ThreadLocalSpan;
import com.mysql.cj.exceptions.ExceptionInterceptor;
import com.mysql.cj.log.Log;
import java.sql.SQLException;
import java.util.Properties;

/**
 * A MySQL exception interceptor that will annotate spans with SQL error codes.
 *
 * <p>To use it, both TracingQueryInterceptor and TracingExceptionInterceptor must be added by
 * appending <code>?queryInterceptors=brave.mysql8.TracingQueryInterceptor&exceptionInterceptors=brave.mysql8.TracingExceptionInterceptor</code>.
 */
public class TracingExceptionInterceptor implements ExceptionInterceptor {

  @Override public ExceptionInterceptor init(Properties properties, Log log) {
    String queryInterceptors = properties.getProperty("queryInterceptors");
    if (queryInterceptors == null ||
      !queryInterceptors.contains(TracingQueryInterceptor.class.getName())) {
      throw new IllegalStateException(
        "TracingQueryInterceptor must be enabled to use TracingExceptionInterceptor.");
    }
    return new TracingExceptionInterceptor();
  }

  @Override public void destroy() {
    // Don't care
  }

  /**
   * Uses {@link ThreadLocalSpan} as there's no attribute namespace shared between callbacks, but
   * all callbacks happen on the same thread. The span will already have been created in {@link
   * TracingQueryInterceptor}.
   *
   * <p>Uses {@link ThreadLocalSpan#CURRENT_TRACER} and this interceptor initializes before
   * tracing.
   */
  @Override public Exception interceptException(Exception e) {
    Span span = ThreadLocalSpan.CURRENT_TRACER.remove();
    if (span == null || span.isNoop()) return null;

    if (e instanceof SQLException) {
      span.tag("error", Integer.toString(((SQLException) e).getErrorCode()));
    }

    span.finish();

    return null;
  }
}
