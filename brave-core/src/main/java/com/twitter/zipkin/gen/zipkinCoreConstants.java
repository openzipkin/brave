package com.twitter.zipkin.gen;

/**
 * @deprecated use {@link zipkin.Constants}; will be removed in Brave 2.0
 */
@Deprecated
public class zipkinCoreConstants {

  /**
   * The client sent ("cs") a request to a server. There is only one send per
   * span. For example, if there's a transport error, each attempt can be logged
   * as a WIRE_SEND annotation.
   * 
   * If chunking is involved, each chunk could be logged as a separate
   * CLIENT_SEND_FRAGMENT in the same span.
   * 
   * Annotation.host is not the server. It is the host which logged the send
   * event, almost always the client. When logging CLIENT_SEND, instrumentation
   * should also log the SERVER_ADDR.
   */
  public static final String CLIENT_SEND = "cs";

  /**
   * The client received ("cr") a response from a server. There is only one
   * receive per span. For example, if duplicate responses were received, each
   * can be logged as a WIRE_RECV annotation.
   * 
   * If chunking is involved, each chunk could be logged as a separate
   * CLIENT_RECV_FRAGMENT in the same span.
   * 
   * Annotation.host is not the server. It is the host which logged the receive
   * event, almost always the client. The actual endpoint of the server is
   * recorded separately as SERVER_ADDR when CLIENT_SEND is logged.
   */
  public static final String CLIENT_RECV = "cr";

  /**
   * The server sent ("ss") a response to a client. There is only one response
   * per span. If there's a transport error, each attempt can be logged as a
   * WIRE_SEND annotation.
   * 
   * Typically, a trace ends with a server send, so the last timestamp of a trace
   * is often the timestamp of the root span's server send.
   * 
   * If chunking is involved, each chunk could be logged as a separate
   * SERVER_SEND_FRAGMENT in the same span.
   * 
   * Annotation.host is not the client. It is the host which logged the send
   * event, almost always the server. The actual endpoint of the client is
   * recorded separately as CLIENT_ADDR when SERVER_RECV is logged.
   */
  public static final String SERVER_SEND = "ss";

  /**
   * The server received ("sr") a request from a client. There is only one
   * request per span.  For example, if duplicate responses were received, each
   * can be logged as a WIRE_RECV annotation.
   * 
   * Typically, a trace starts with a server receive, so the first timestamp of a
   * trace is often the timestamp of the root span's server receive.
   * 
   * If chunking is involved, each chunk could be logged as a separate
   * SERVER_RECV_FRAGMENT in the same span.
   * 
   * Annotation.host is not the client. It is the host which logged the receive
   * event, almost always the server. When logging SERVER_RECV, instrumentation
   * should also log the CLIENT_ADDR.
   */
  public static final String SERVER_RECV = "sr";

  /**
   * Optionally logs an attempt to send a message on the wire. Multiple wire send
   * events could indicate network retries. A lag between client or server send
   * and wire send might indicate queuing or processing delay.
   */
  public static final String WIRE_SEND = "ws";

  /**
   * Optionally logs an attempt to receive a message from the wire. Multiple wire
   * receive events could indicate network retries. A lag between wire receive
   * and client or server receive might indicate queuing or processing delay.
   */
  public static final String WIRE_RECV = "wr";

  /**
   * Optionally logs progress of a (CLIENT_SEND, WIRE_SEND). For example, this
   * could be one chunk in a chunked request.
   */
  public static final String CLIENT_SEND_FRAGMENT = "csf";

  /**
   * Optionally logs progress of a (CLIENT_RECV, WIRE_RECV). For example, this
   * could be one chunk in a chunked response.
   */
  public static final String CLIENT_RECV_FRAGMENT = "crf";

  /**
   * Optionally logs progress of a (SERVER_SEND, WIRE_SEND). For example, this
   * could be one chunk in a chunked response.
   */
  public static final String SERVER_SEND_FRAGMENT = "ssf";

  /**
   * Optionally logs progress of a (SERVER_RECV, WIRE_RECV). For example, this
   * could be one chunk in a chunked request.
   */
  public static final String SERVER_RECV_FRAGMENT = "srf";

  /**
   * Indicates a client address ("ca") in a span. Most likely, there's only one.
   * Multiple addresses are possible when a client changes its ip or port within
   * a span.
   */
  public static final String CLIENT_ADDR = "ca";

  /**
   * Indicates a server address ("sa") in a span. Most likely, there's only one.
   * Multiple addresses are possible when a client is redirected, or fails to a
   * different server ip or port.
   */
  public static final String SERVER_ADDR = "sa";

  /**
   * The {@link BinaryAnnotation#value value} of "lc" is the component or namespace of a local
   * span.
   *
   * <p/>{@link BinaryAnnotation#host} adds service context needed to support queries.
   *
   * <p/>Local Component("lc") supports three key features: flagging, query by service and filtering
   * Span.name by namespace.
   *
   * <p/>While structurally the same, local spans are fundamentally different than RPC spans in how
   * they should be interpreted. For example, zipkin v1 tools center on RPC latency and service
   * graphs. Root local-spans are neither indicative of critical path RPC latency, nor have impact
   * on the shape of a service graph. By flagging with "lc", tools can special-case local spans.
   *
   * <p/>Zipkin v1 Spans are unqueryable unless they can be indexed by service name. The only path
   * to a {@link Endpoint#service_name service name} is via {@link BinaryAnnotation#host
   * host}. By logging "lc", a local span can be queried even if no other annotations are logged.
   *
   * <p/>The value of "lc" is the namespace of {@link Span#name}. For example, it might be
   * "finatra2", for a span named "bootstrap". "lc" allows you to resolves conflicts for the same
   * Span.name, for example "finatra/bootstrap" vs "finch/bootstrap". Using local component, you'd
   * search for spans named "bootstrap" where "lc=finch"
   */
  public static final String LOCAL_COMPONENT = "lc";
}
