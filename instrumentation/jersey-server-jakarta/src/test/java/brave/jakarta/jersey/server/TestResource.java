/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jersey.server;

import brave.Tracer;
import brave.http.HttpTracing;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ManagedAsync;

import static brave.test.ITRemote.BAGGAGE_FIELD;
import static brave.test.http.ITHttpServer.NOT_READY_ISE;

@Path("")
public class TestResource {
  final Tracer tracer;

  TestResource(HttpTracing httpTracing) {
    this.tracer = httpTracing.tracing().tracer();
  }

  @OPTIONS // intentionally leave out the @Path annotation
  public Response root() {
    return Response.ok().build();
  }

  @GET
  @Path("foo")
  public Response foo() {
    return Response.ok().build();
  }

  @GET
  @Path("extra")
  public Response extra() {
    return Response.ok(BAGGAGE_FIELD.getValue()).build();
  }

  @GET
  @Path("badrequest")
  public Response badrequest() {
    return Response.status(400).build();
  }

  @GET
  @Path("child")
  public Response child() {
    tracer.nextSpan().name("child").start().finish();
    return Response.status(200).build();
  }

  @GET
  @Path("async")
  public void async(@Suspended AsyncResponse response) {
    if (tracer.currentSpan() == null) {
      response.resume(new IllegalStateException("couldn't read current span!"));
      return;
    }
    blockOnAsyncResult("foo", response);
  }

  @GET
  @Path("managedAsync")
  @ManagedAsync
  public void managedAsync(@Suspended AsyncResponse response) {
    if (tracer.currentSpan() == null) {
      response.resume(new IllegalStateException("couldn't read current span!"));
      return;
    }
    response.resume("foo");
  }

  @GET
  @Path("items/{itemId}")
  public String item(@PathParam("itemId") String itemId) {
    return itemId;
  }

  @GET
  @Path("async_items/{itemId}")
  public void asyncItem(@PathParam("itemId") String itemId, @Suspended AsyncResponse response) {
    blockOnAsyncResult(itemId, response);
  }

  static void blockOnAsyncResult(String body, AsyncResponse response) {
    Thread thread = new Thread(() -> response.resume(body));
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException e) {
    }
  }

  @GET
  @Path("exception")
  public Response notReady() {
    throw new WebApplicationException(NOT_READY_ISE, 503);
  }

  @GET
  @Path("exceptionAsync")
  public void notReadyAsync(@Suspended AsyncResponse response) {
    Thread thread = new Thread(() -> response.resume(
      new WebApplicationException(NOT_READY_ISE, 503)
    ));
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException e) {
    }
  }

  public static class NestedResource {
    @GET
    @Path("items/{itemId}")
    public String item(@PathParam("itemId") String itemId) {
      return itemId;
    }
  }

  @Path("nested")
  public NestedResource nestedResource() {
    return new NestedResource();
  }
}
