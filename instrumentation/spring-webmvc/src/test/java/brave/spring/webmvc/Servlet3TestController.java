/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.webmvc;

import brave.http.HttpTracing;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import static brave.test.http.ITServlet25Container.NOT_READY_UE;

@Controller class Servlet3TestController extends Servlet25TestController {

  @Autowired Servlet3TestController(HttpTracing httpTracing) {
    super(httpTracing);
  }

  @RequestMapping(value = "/async")
  public Callable<ResponseEntity<Void>> async() {
    return () -> new ResponseEntity<>(HttpStatus.OK);
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
  @RequestMapping(value = "/exceptionAsync")
  public Callable<ResponseEntity<Void>> notReadyAsync() {
    return () -> {
      throw NOT_READY_UE;
    };
  }

  @RequestMapping(value = "/async_items/{itemId}")
  public Callable<ResponseEntity<String>> asyncItems(@PathVariable("itemId") String itemId) {
    return () -> new ResponseEntity<String>(itemId, HttpStatus.OK);
  }
}
