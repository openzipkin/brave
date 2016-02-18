package com.twitter.zipkin.gen;

import com.github.kristofa.brave.internal.Nullable;
import java.io.Serializable;

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
public class Endpoint implements Serializable {

  public static Endpoint create(String serviceName, int ipv4, int port) {
    return new Endpoint(serviceName, ipv4, (short) (port & 0xffff));
  }

  public static Endpoint create(String serviceName, int ipv4) {
    return new Endpoint(serviceName, ipv4, null);
  }

  static final long serialVersionUID = 1L;

  /**
   * IPv4 host address packed into 4 bytes.
   *
   * Ex for the ip 1.2.3.4, it would be (1 << 24) | (2 << 16) | (3 << 8) | 4
   */
  public final int ipv4; // required

  /**
   * IPv4 port
   *
   * Note: this is to be treated as an unsigned integer, so watch for negatives.
   *
   * Null, when the port isn't known.
   */
  @Nullable
  public final Short port; // required

  /**
   * Service name in lowercase, such as "memcache" or "zipkin-web"
   *
   * Conventionally, when the service name isn't known, service_name = "unknown".
   */
  public final String service_name; // required

  Endpoint(String service_name, int ipv4, Short port) {
    this.ipv4 = ipv4;
    this.port = port;
    if (service_name != null) {
      service_name = service_name.toLowerCase();
    } else {
      service_name = "";
    }
    this.service_name = service_name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Endpoint) {
      Endpoint that = (Endpoint) o;
      return this.service_name.equals(that.service_name)
          && this.ipv4 == that.ipv4
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
    h ^= (port == null) ? 0 : port.hashCode();
    return h;
  }
}

