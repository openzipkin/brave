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
package brave.spring.webmvc;

import brave.Tracer;
import brave.http.HttpTracing;
import brave.propagation.ExtraFieldPropagation;
import brave.test.http.ITHttp;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller class Servlet25TestController {
  final Tracer tracer;

  @Autowired Servlet25TestController(HttpTracing httpTracing) {
    this.tracer = httpTracing.tracing().tracer();
  }

  @RequestMapping(method = RequestMethod.OPTIONS, value = "/")
  public ResponseEntity<Void> root() {
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @RequestMapping(value = "/foo")
  public ResponseEntity<Void> foo() {
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @RequestMapping(value = "/extra")
  public ResponseEntity<String> extra() {
    return new ResponseEntity<>(ExtraFieldPropagation.get(ITHttp.EXTRA_KEY), HttpStatus.OK);
  }

  @RequestMapping(value = "/badrequest")
  public ResponseEntity<Void> badrequest() {
    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
  }

  @RequestMapping(value = "/child")
  public ResponseEntity<Void> child() {
    tracer.nextSpan().name("child").start().finish();
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @RequestMapping(value = "/exception")
  public ResponseEntity<Void> disconnect() throws IOException {
    throw new IOException();
  }

  @RequestMapping(value = "/items/{itemId}")
  public ResponseEntity<String> items(@PathVariable("itemId") String itemId) {
    return new ResponseEntity<String>(itemId, HttpStatus.OK);
  }

  @Controller
  @RequestMapping(value = "/nested")
  static class NestedController {
    @RequestMapping(value = "/items/{itemId}")
    public ResponseEntity<String> items(@PathVariable("itemId") String itemId) {
      return new ResponseEntity<String>(itemId, HttpStatus.OK);
    }
  }
}
