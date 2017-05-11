package com.github.kristofa.brave.grpc;

import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import com.github.kristofa.brave.grpc.BraveGrpcServerInterceptor.GrpcServerRequestAdapter;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.examples.helloworld.GreeterGrpc;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GrpcServerRequestAdapterTest {
  private static final String TRACE_ID = "7a842183262a6c62";
  private static final String SPAN_ID = "bf38b90488a1e481";
  private static final String PARENT_SPAN_ID = "8000000000000000";

  Metadata metadata = new Metadata();
  GrpcServerRequestAdapter adapter;

  @Before
  public void initMocks() {
    ServerCall serverCall = mock(ServerCall.class);
    when(serverCall.getMethodDescriptor()).thenReturn(GreeterGrpc.METHOD_SAY_HELLO);

    adapter = new GrpcServerRequestAdapter(serverCall, metadata);
  }

  @Test
  public void getTraceDataNoSampledHeader() {
    TraceData traceData = adapter.getTraceData();
    assertNotNull(traceData);
    assertNull(traceData.getSample());
    assertNull(traceData.getSpanId());
  }

  @Test
  public void getTraceDataSampledFalse() {
    metadata.put(BravePropagationKeys.Sampled, "false");

    TraceData traceData = adapter.getTraceData();
    assertNotNull(traceData);
    assertFalse(traceData.getSample());
    assertNull(traceData.getSpanId());
  }

  @Test
  public void getTraceDataSampledFalseUpperCase() {
    metadata.put(BravePropagationKeys.Sampled, "FALSE");

    TraceData traceData = adapter.getTraceData();
    assertNotNull(traceData);
    assertFalse(traceData.getSample());
    assertNull(traceData.getSpanId());
  }

  /**
   * This is according to the zipkin 'spec'.
   */
  @Test
  public void getTraceDataSampledZero() {
    metadata.put(BravePropagationKeys.Sampled, "0");

    TraceData traceData = adapter.getTraceData();
    assertNotNull(traceData);
    assertFalse(traceData.getSample());
    assertNull(traceData.getSpanId());
  }

  @Test
  public void getTraceDataSampledTrueNoOtherTraceHeaders() {
    metadata.put(BravePropagationKeys.Sampled, "1");

    TraceData traceData = adapter.getTraceData();
    assertNotNull(traceData);
    assertNull(traceData.getSample());
    assertNull(traceData.getSpanId());
  }

  @Test
  public void getTraceDataSampledTrueNoParentId() {
    metadata.put(BravePropagationKeys.Sampled, "true");
    metadata.put(BravePropagationKeys.TraceId, TRACE_ID);
    metadata.put(BravePropagationKeys.SpanId, SPAN_ID);

    TraceData traceData = adapter.getTraceData();
    assertNotNull(traceData);
    assertTrue(traceData.getSample());
    SpanId spanId = traceData.getSpanId();
    assertNotNull(spanId);
    assertEquals(IdConversion.convertToLong(TRACE_ID), spanId.traceId);
    assertEquals(IdConversion.convertToLong(SPAN_ID), spanId.spanId);
    assertNull(spanId.nullableParentId());
  }

  /**
   * This is according to the zipkin 'spec'.
   */
  @Test
  public void getTraceDataSampledOneNoParentId() {
    metadata.put(BravePropagationKeys.Sampled, "1");
    metadata.put(BravePropagationKeys.TraceId, TRACE_ID);
    metadata.put(BravePropagationKeys.SpanId, SPAN_ID);
    TraceData traceData = adapter.getTraceData();
    assertNotNull(traceData);
    assertTrue(traceData.getSample());
    SpanId spanId = traceData.getSpanId();
    assertNotNull(spanId);
    assertEquals(IdConversion.convertToLong(TRACE_ID), spanId.traceId);
    assertEquals(IdConversion.convertToLong(SPAN_ID), spanId.spanId);
    assertNull(spanId.nullableParentId());
  }

  @Test
  public void supports128BitTraceIdHeader() {
    String upper64Bits = "48485a3953bb6124";
    String lower64Bits = "48485a3953bb6124";
    String hex128Bits = upper64Bits + lower64Bits;
    metadata.put(BravePropagationKeys.Sampled, "1");
    metadata.put(BravePropagationKeys.TraceId, hex128Bits);
    metadata.put(BravePropagationKeys.SpanId, lower64Bits);
    TraceData traceData = adapter.getTraceData();
    assertNotNull(traceData);
    assertTrue(traceData.getSample());
    SpanId spanId = traceData.getSpanId();
    assertNotNull(spanId);
    assertEquals(IdConversion.convertToLong(upper64Bits), spanId.traceIdHigh);
    assertEquals(IdConversion.convertToLong(lower64Bits), spanId.traceId);
    assertEquals(IdConversion.convertToLong(lower64Bits), spanId.spanId);
    assertNull(spanId.nullableParentId());
  }

  @Test
  public void getTraceDataSampledTrueWithParentId() {
    metadata.put(BravePropagationKeys.Sampled, "true");
    metadata.put(BravePropagationKeys.TraceId, TRACE_ID);
    metadata.put(BravePropagationKeys.SpanId, SPAN_ID);
    metadata.put(BravePropagationKeys.ParentSpanId, PARENT_SPAN_ID);

    TraceData traceData = adapter.getTraceData();
    assertNotNull(traceData);
    assertTrue(traceData.getSample());
    SpanId spanId = traceData.getSpanId();
    assertNotNull(spanId);
    assertEquals(IdConversion.convertToLong(TRACE_ID), spanId.traceId);
    assertEquals(IdConversion.convertToLong(SPAN_ID), spanId.spanId);
    assertEquals(IdConversion.convertToLong(PARENT_SPAN_ID), spanId.parentId);
  }

  /**
   * When the caller propagates IDs, but not a sampling decision, the local process should decide.
   */
  @Test
  public void getTraceData_externallyProvidedIds() {
    metadata.put(BravePropagationKeys.TraceId, TRACE_ID);
    metadata.put(BravePropagationKeys.SpanId, SPAN_ID);
    TraceData traceData = adapter.getTraceData();
    assertNotNull(traceData);
    assertNull(traceData.getSample());
    SpanId spanId = traceData.getSpanId();
    assertNotNull(spanId);
    assertEquals(IdConversion.convertToLong(TRACE_ID), spanId.traceId);
    assertEquals(IdConversion.convertToLong(SPAN_ID), spanId.spanId);
    assertNull(spanId.nullableParentId());
  }
}
