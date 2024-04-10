/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.webmvc;

import brave.Tracer;
import brave.http.HttpTracing;
import javax.servlet.UnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import static brave.test.ITRemote.BAGGAGE_FIELD;
import static brave.test.http.ITServlet25Container.NOT_READY_UE;

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

  @RequestMapping(value = "/baggage")
  public ResponseEntity<String> baggage() {
    return new ResponseEntity<>(BAGGAGE_FIELD.getValue(), HttpStatus.OK);
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

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
  @RequestMapping(value = "/exception")
  public ResponseEntity<Void> notReady() throws UnavailableException {
    throw NOT_READY_UE;
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
