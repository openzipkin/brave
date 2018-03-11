package brave.jersey.server;

import brave.SpanCustomizer;
import brave.http.HttpTracing;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

/**
 * Jersey specific type used to customize traced requests based on the JAX-RS resource.
 *
 * <p>Note: This should not duplicate data added by {@link HttpTracing}. For example, this should
 * not add the tag "http.url".
 */
// named event parser, not request event parser, in case we want to later support application event.
public class EventParser {
  /** Adds no data to the request */
  public static final EventParser NOOP = new EventParser() {
    @Override protected void requestMatched(RequestEvent event, SpanCustomizer customizer) {
    }
  };

  /** Simple class name that processed the request. ex BookResource */
  public static final String RESOURCE_CLASS = "jaxrs.resource.class";
  /** Method name that processed the request. ex listOfBooks */
  public static final String RESOURCE_METHOD = "jaxrs.resource.method";

  /**
   * Invoked prior to request invocation during {@link RequestEventListener#onEvent(RequestEvent)}
   * where the event type is {@link RequestEvent.Type#REQUEST_MATCHED}
   *
   * <p>Adds the tags {@link #RESOURCE_CLASS} and {@link #RESOURCE_METHOD}. Override or use {@link #NOOP}
   * to change this behavior.
   */
  protected void requestMatched(RequestEvent event, SpanCustomizer customizer) {
    Invocable i =
        event.getContainerRequest().getUriInfo().getMatchedResourceMethod().getInvocable();
    customizer.tag(RESOURCE_CLASS, i.getHandler().getHandlerClass().getSimpleName());
    customizer.tag(RESOURCE_METHOD, i.getHandlingMethod().getName());
  }

  public EventParser() { // intentionally public for @Inject to work without explicit binding
  }
}
