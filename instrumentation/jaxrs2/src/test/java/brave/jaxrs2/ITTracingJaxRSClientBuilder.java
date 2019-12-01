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
package brave.jaxrs2;

import brave.Span;
import brave.test.http.ITHttpAsyncClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.Ignore;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.MediaType;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ITTracingJaxRSClientBuilder extends ITHttpAsyncClient<Client> {
  @Override protected Client newClient(int port) {
    return new ResteasyClientBuilder()
      .register(TracingClientFilter.create(httpTracing))
      .connectTimeout(1, TimeUnit.SECONDS)
      .readTimeout(1, TimeUnit.SECONDS)
      .executorService(httpTracing.tracing().currentTraceContext()
        .executorService(Executors.newCachedThreadPool()))
      .build();
  }

  @Override protected void closeClient(Client client) {
    client.close();
  }

  @Override protected void get(Client client, String pathIncludingQuery) {
    try {
      client.target(url(pathIncludingQuery))
        .request(MediaType.TEXT_PLAIN_TYPE)
        .get(String.class);
    } catch (ProcessingException ex) {
      Span span = tracer().currentSpan();
      if (null != span) {
        span.error(ex).finish();
      }
      throw ex;
    }
  }

  @Override protected void getAsync(Client client, String pathIncludingQuery) {
    client.target(url(pathIncludingQuery))
      .request(MediaType.TEXT_PLAIN_TYPE)
      .async()
      .get(new InvocationCallback<String>() {
        @Override public void completed(String response) {
        }

        @Override public void failed(Throwable throwable) {
          Span span = tracer().currentSpan();
          if (null != span) {
            span.error(throwable).finish();
          }
          throwable.printStackTrace();
        }
      });
  }

  @Override
  protected void post(Client client, String pathIncludingQuery, String body) {
      try {
        client.target(url(pathIncludingQuery))
          .request(MediaType.TEXT_PLAIN_TYPE)
          .post(Entity.text(body), String.class);
      } catch (ProcessingException ex) {
        Span span = tracer().currentSpan();
        if (null != span) {
          span.error(ex).finish();
        }
        throw ex;
      }
  }

  @Override @Ignore("blind to the implementation of redirects")
  public void redirect() {
  }

  @Override @Ignore("doesn't know the remote address")
  public void reportsServerAddress() {
  }
}
