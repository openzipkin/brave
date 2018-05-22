package brave.spring.beans;

import org.springframework.beans.factory.FactoryBean;
import zipkin2.Endpoint;

/** Spring XML config does not support chained builders. This converts accordingly */
public class EndpointFactoryBean implements FactoryBean {

  String serviceName;
  String ip;
  Integer port;

  @Override public Endpoint getObject() {
    Endpoint.Builder builder = Endpoint.newBuilder();
    if (serviceName != null) builder.serviceName(serviceName);
    if (ip != null && !builder.parseIp(ip)) {
      throw new IllegalArgumentException("endpoint.ip: " + ip + " is not an IP literal");
    }
    if (port != null) builder.port(port);
    return builder.build();
  }

  @Override public Class<? extends Endpoint> getObjectType() {
    return Endpoint.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public void setPort(Integer port) {
    this.port = port;
  }
}
