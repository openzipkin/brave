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

import brave.http.HttpTracing;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller class Servlet3TestController extends Servlet25TestController {

  @Autowired Servlet3TestController(HttpTracing httpTracing) {
    super(httpTracing);
  }

  @RequestMapping(value = "/async")
  public Callable<ResponseEntity<Void>> async() {
    return () -> new ResponseEntity<>(HttpStatus.OK);
  }

  @RequestMapping(value = "/exceptionAsync")
  public Callable<ResponseEntity<Void>> disconnectAsync() {
    return () -> {
      throw new IOException();
    };
  }

  @RequestMapping(value = "/async_items/{itemId}")
  public Callable<ResponseEntity<String>> asyncItems(@PathVariable("itemId") String itemId) {
    return () -> new ResponseEntity<String>(itemId, HttpStatus.OK);
  }
}
