package com.twitter.zipkin.gen;

import java.io.Serializable;
import java.util.Arrays;
import javax.annotation.Generated;
import javax.annotation.Nullable;

import static com.github.kristofa.brave.internal.Util.checkNotNull;
import static com.github.kristofa.brave.internal.Util.equal;

/**
 * Indicates the network context of a service recording an annotation with two
 * exceptions.
 *
 * When a BinaryAnnotation, and key is CLIENT_ADDR or SERVER_ADDR,
 * the endpoint indicates the source or destination of an RPC. This exception
 * allows zipkin to display network context of uninstrumented services, or
 * clients such as web browsers.
 */
@Generated("thrift")
public class Endpoint implements Serializable {

  /**
   * @deprecated as leads to null pointer exceptions on port. Use {@link #builder()} instead.
   */
  @Deprecated
  public static Endpoint create(String serviceName, int ipv4, int port) {
    return new Endpoint(serviceName, ipv4, null, port == 0 ? null : (short) (port & 0xffff));
  }

  public static Endpoint create(String serviceName, int ipv4) {
    return new Endpoint(serviceName, ipv4, null, null);
  }

  static final long serialVersionUID = 1L;

  /**
   * IPv4 host address packed into 4 bytes or zero if unknown.
   *
   * <p>Ex for the IP 1.2.3.4, it would be {@code (1 << 24) | (2 << 16) | (3 << 8) | 4}
   *
   * @see java.net.Inet4Address#getAddress()
   */
  public final int ipv4;

  /**
   * IPv6 host address packed into 16 bytes or null if unknown.
   *
   * @see java.net.Inet6Address#getAddress()
   * @since 3.12
   */
  @Nullable
  public final byte[] ipv6;

  /**
   * Port of the IP's socket or null, if not known.
   *
   * <p>Note: this is to be treated as an unsigned integer, so watch for negatives.
   *
   * @see java.net.InetSocketAddress#getPort()
   */
  @Nullable
  public final Short port;

  /**
   * Service name in lowercase, such as "memcache" or "zipkin-web"
   *
   * Conventionally, when the service name isn't known, service_name = "unknown".
   */
  public final String service_name; // required

  Endpoint(String service_name, int ipv4, byte[] ipv6, Short port) {
    this.ipv4 = ipv4;
    this.ipv6 = ipv6;
    this.port = port;
    if (service_name != null) {
      service_name = service_name.toLowerCase();
    } else {
      service_name = "";
    }
    this.service_name = service_name;
  }

  public Endpoint.Builder toBuilder() {
    return new Endpoint.Builder(this);
  }

  public static Endpoint.Builder builder() {
    return new Endpoint.Builder();
  }

  public static final class Builder {
    private String serviceName;
    private Integer ipv4;
    private byte[] ipv6;
    private Short port;

    Builder() {
    }

    Builder(Endpoint source) {
      this.serviceName = source.service_name;
      this.ipv4 = source.ipv4;
      this.ipv6 = source.ipv6;
      this.port = source.port;
    }

    /** @see Endpoint#service_name */
    public Endpoint.Builder serviceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    /** @see Endpoint#ipv4 */
    public Endpoint.Builder ipv4(int ipv4) {
      this.ipv4 = ipv4;
      return this;
    }

    /** @see Endpoint#ipv6 */
    public Endpoint.Builder ipv6(byte[] ipv6) {
      if (ipv6 != null) {
        if (ipv6.length != 16) throw new IllegalArgumentException("ipv6.length != 16");
        this.ipv6 = ipv6;
      }
      return this;
    }

    /**
     * Use this to set the port to an externally defined value.
     *
     * <p>Don't pass {@link Endpoint#port} to this method, as it may result in a
     * NullPointerException. Instead, use {@link Endpoint#toBuilder()} or {@link
     * #port(Short)}.
     *
     * @param port port associated with the endpoint. zero coerces to null (unknown)
     * @see Endpoint#port
     */
    public Endpoint.Builder port(int port) {
      if (port < 0 || port > 0xffff) throw new IllegalArgumentException("invalid port " + port);
      this.port = port == 0 ? null : (short) (port & 0xffff);
      return this;
    }

    /** @see Endpoint#port */
    public Endpoint.Builder port(Short port) {
      if (port == null || port != 0) {
        this.port = port;
      }
      return this;
    }

    public Endpoint build() {
      checkNotNull(serviceName, "serviceName");
      return new Endpoint(serviceName, ipv4 == null ? 0 : ipv4, ipv6, port);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Endpoint) {
      Endpoint that = (Endpoint) o;
      return equal(this.service_name, that.service_name)
          && (this.ipv4 == that.ipv4)
          && (Arrays.equals(this.ipv6, that.ipv6))
          && equal(this.port, that.port);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= service_name.hashCode();
    h *= 1000003;
    h ^= ipv4;
    h *= 1000003;
    h ^= Arrays.hashCode(ipv6);
    h *= 1000003;
    h ^= (port == null) ? 0 : port.hashCode();
    return h;
  }

  /** Returns a json representation of this endpoint */
  @Override public String toString() {
    // zipkin.Endpoint.toString is json
    return zipkin.Endpoint.builder()
        .serviceName(service_name)
        .port(port)
        .ipv4(ipv4)
        .ipv6(ipv6).build().toString();
  }
}

