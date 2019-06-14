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

import brave.Tracing;
import brave.propagation.ExtraFieldPropagation;
import spark.Spark;
import spark.servlet.SparkApplication;

import static brave.test.http.ITHttp.EXTRA_KEY;

public class TestApplication implements SparkApplication {
  @Override public void init() {
    Spark.options("/", (req, res) -> "");
    Spark.get("/foo", (req, res) -> "bar");
    Spark.get("/extra", (req, res) -> ExtraFieldPropagation.get(EXTRA_KEY));
    Spark.get("/badrequest", (req, res) -> {
      res.status(400);
      return res;
    });
    Spark.get("/child", (req, res) -> {
      Tracing.currentTracer().nextSpan().name("child").start().finish();
      return "happy";
    });
    Spark.get("/exception", (req, res) -> {
      throw new Exception();
    });

    // TODO: we need matchUri: https://github.com/perwendel/spark/issues/959
    //Spark.get("/items/:itemId", (request, response) -> request.params(":itemId"));
    //Spark.path("/nested", () ->
    //    Spark.get("/items/:itemId", (request, response) -> request.params(":itemId"))
    //);
  }
}
