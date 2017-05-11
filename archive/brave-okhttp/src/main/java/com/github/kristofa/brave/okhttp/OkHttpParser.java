package com.github.kristofa.brave.okhttp;

import com.github.kristofa.brave.KeyValueAnnotation;
import java.util.Collections;
import java.util.List;
import okhttp3.Request;
import okhttp3.Response;
import zipkin.TraceKeys;

/**
 * Extend this type to change metadata recorded in spans representing http operations.
 *
 * <p><em>Be careful when customizing</em>, particularly not to add too much data. Large spans (ex
 * large orders of kilobytes) can be problematic and/or dropped. Also, be careful that span names
 * have low cardinality (ex no embedded variables). Finally, prefer names in {@link TraceKeys} where
 * possible, so that lookup keys are coherent.
 *
 * For more information, look at our <a href="http://zipkin.io/pages/instrumenting.html">instrumentation
 * docs</a>
 */
public class OkHttpParser {

  /** Returns the {@link Request#tag()} or the http method when absent. */
  public String applicationSpanName(Request request) {
    return !(request.tag() instanceof Request)
        ? request.tag().toString()
        : request.method();
  }

  /** Returns the http method. */
  public String networkSpanName(Request request) {
    return request.method();
  }

  /** Returns the {@link zipkin.TraceKeys#HTTP_URL} */
  public List<KeyValueAnnotation> networkRequestTags(Request request) {
    return Collections.singletonList(
        KeyValueAnnotation.create(TraceKeys.HTTP_URL, request.url().toString())
    );
  }

  /** Returns the {@link zipkin.TraceKeys#HTTP_STATUS_CODE} if unsuccessful */
  public List<KeyValueAnnotation> networkResponseTags(Response response) {
    int code = response.code();
    if (response.isSuccessful()) return Collections.EMPTY_LIST;

    return Collections.singletonList(
        KeyValueAnnotation.create(TraceKeys.HTTP_STATUS_CODE, String.valueOf(code))
    );
  }
}
