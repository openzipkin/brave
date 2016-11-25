# brave-web-servlet-filter

The module contains a Servlet filter (javax.servlet.filter) to deal with server
side integration: Getting existing span/trace state from request,
create and submit span with `sr`', `ss` annotations.

Example Servlet API 3.0 Java Config

```java
public class WebAppInitializer implements WebApplicationInitializer {

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {

        Brave brave = new Brave.Builder("myservicename")).build();
        servletContext.addFilter(“BraveServletFilter”, BraveServletFilter.create(brave));
    }

}
```