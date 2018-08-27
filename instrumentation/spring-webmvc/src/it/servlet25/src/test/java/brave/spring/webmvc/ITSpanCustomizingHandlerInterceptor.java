package brave.spring.webmvc;

import java.util.EnumSet;
import org.eclipse.jetty.server.DispatcherType;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Test;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

public class ITSpanCustomizingHandlerInterceptor extends BaseITSpanCustomizingHandlerInterceptor {
  @Override protected void addDelegatingTracingFilter(ServletContextHandler handler) {
    handler.addFilter(DelegatingTracingFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
  }

  @Override
  protected void registerTestController(AnnotationConfigWebApplicationContext appContext) {
    appContext.register(Servlet25TestController.class); // the test resource
  }
}
