/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.sparkjava;

import brave.test.http.ITHttpServer;
import brave.test.http.Log4J2Log;
import java.io.IOException;
import okhttp3.Response;
import org.eclipse.jetty.util.log.Log;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import spark.Spark;

public class ITSparkTracing extends ITHttpServer {
  public ITSparkTracing() {
    Log.setLog(new Log4J2Log());
  }

  @Override protected Response get(String path) throws IOException {
    if (path.toLowerCase().indexOf("async") == -1) return super.get(path);
    throw new AssumptionViolatedException(
      "ignored until https://github.com/perwendel/spark/issues/208");
  }

  @Override protected void init()  {
    stop();

    SparkTracing spark = SparkTracing.create(httpTracing);
    Spark.before(spark.before());
    Spark.exception(Exception.class, spark.exception(
      (exception, request, response) -> response.body("exception"))
    );
    Spark.afterAfter(spark.afterAfter());

    new TestApplication().init();
    Spark.awaitInitialization();
  }

  @Override
  protected String url(String path) {//default port 4567
    return "http://localhost:4567" + path;
  }

  @After
  public void stop() {
    Spark.stop();
    Spark.awaitStop();
  }
}
