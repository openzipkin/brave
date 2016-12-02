package com.github.kristofa.brave.spring;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.util.concurrent.Callable;

@Controller
@RequestMapping(value = "/ping", method = RequestMethod.GET)
public class PingController {

    @RequestMapping(value = "/sync")
    public ResponseEntity<Void> sync() throws IOException {
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(value = "/async")
    public Callable<ResponseEntity<Void>> async() throws IOException {
        return () -> ResponseEntity.noContent().build();
    }
}
