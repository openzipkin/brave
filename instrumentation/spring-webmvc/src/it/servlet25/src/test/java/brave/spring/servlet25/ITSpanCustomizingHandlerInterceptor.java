package brave.spring.webmvc;

import java.util.EnumSet;
import org.eclipse.jetty.server.DispatcherType;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class ITSpanCustomizingHandlerInterceptor extends BaseITSpanCustomizingHandlerInterceptor {
  @Override protected void addDelegatingTracingFilter(ServletContextHandler handler) {
    handler.addFilter(DelegatingTracingFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
  }
}
