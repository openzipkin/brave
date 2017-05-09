# brave-web-servlet-filter

The module contains a Servlet filter (javax.servlet.filter) to deal with server
side integration: Getting existing span/trace state from request,
create and submit span with `sr`', `ss` annotations.

Example Servlet API 3.0 Java Config, using a `ServletContextListener` that you create

```java
public class BraveServletContextListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        Brave brave = new Brave.Builder("myservicename").build();
        servletContextEvent
                .getServletContext()
                    .addFilter("BraveServletFilter", BraveServletFilter.create(brave))
                    .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class),true, "/*");

    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {

    }
}
```
