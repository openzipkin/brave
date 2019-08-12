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
package brave.sparkjava;

import brave.test.http.ITHttpServer;
import okhttp3.Response;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import spark.Spark;

public class ITSparkTracing extends ITHttpServer {

  @Override protected Response get(String path) throws Exception {
    if (path.toLowerCase().indexOf("async") == -1) return super.get(path);
    throw new AssumptionViolatedException(
      "ignored until https://github.com/perwendel/spark/issues/208");
  }

  @Override protected void init() throws Exception {
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

  /**
   * Spark stop asynchronously but share one class Instance, so AddressAlreadyUsed Exception may
   * happen. See:https://github.com/perwendel/spark/issues/705 . Just sleep 1 second to avoid this
   * happens, after Spark.awaitStopped add,I will fix it.
   */
  @After
  public void stop() throws InterruptedException {
    Spark.stop();
    Thread.sleep(1000);
  }
}
