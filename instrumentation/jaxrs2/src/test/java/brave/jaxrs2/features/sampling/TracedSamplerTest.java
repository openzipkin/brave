package brave.jaxrs2.features.sampling;

import brave.http.HttpAdapter;
import brave.http.HttpSampler;
import brave.jaxrs2.ContainerAdapter;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracedSamplerTest {
  @Mock ContainerRequestContext request;
  @Mock ResourceInfo info;

  @Test public void requiresContainerAdapter() {
    Traced.Sampler sampler = new Traced.Sampler(HttpSampler.NEVER_SAMPLE);

    assertThat(sampler.trySample(mock(HttpAdapter.class), new Object()))
        .isFalse();
  }

  @Test public void requiresContainerRequest() {
    Traced.Sampler sampler = new Traced.Sampler(HttpSampler.NEVER_SAMPLE);

    assertThat(sampler.trySample((HttpAdapter) new ContainerAdapter(), new Object()))
        .isFalse();
  }

  @Test public void requiresResourceInfo() {
    Traced.Sampler sampler = new Traced.Sampler(HttpSampler.NEVER_SAMPLE);
    when(request.getProperty("javax.ws.rs.container.ResourceInfo")).thenReturn(info);

    assertThat(sampler.trySample(new ContainerAdapter(), request))
        .isFalse();
  }

  @Path("")
  public static class Resource { // public for resteasy to inject

    @GET
    @Path("foo")
    @Traced
    public String traced() {
      return "";
    }

    @GET
    @Path("bar")
    @Traced(enabled = false) // overrides path-based decision to trace /bar
    public String disabled() {
      return "";
    }

    @GET
    @Path("baz")
    public String noAnnotation() {
      return "";
    }
  }

  @Test public void parsesAnnotation_traced() throws NoSuchMethodException {
    Traced.Sampler sampler = new Traced.Sampler(HttpSampler.NEVER_SAMPLE);
    when(request.getProperty("javax.ws.rs.container.ResourceInfo")).thenReturn(info);
    when(info.getResourceMethod()).thenReturn(Resource.class.getMethod("traced"));

    assertThat(sampler.trySample(new ContainerAdapter(), request))
        .isTrue();
  }

  @Test public void parsesAnnotation_disabled() throws NoSuchMethodException {
    Traced.Sampler sampler = new Traced.Sampler(HttpSampler.NEVER_SAMPLE);
    when(request.getProperty("javax.ws.rs.container.ResourceInfo")).thenReturn(info);
    when(info.getResourceMethod()).thenReturn(Resource.class.getMethod("disabled"));

    assertThat(sampler.trySample(new ContainerAdapter(), request))
        .isFalse();
  }

  @Test public void fallsBackOnNull() throws NoSuchMethodException {
    Traced.Sampler sampler = new Traced.Sampler(HttpSampler.NEVER_SAMPLE);
    when(request.getProperty("javax.ws.rs.container.ResourceInfo")).thenReturn(info);
    when(info.getResourceMethod()).thenReturn(Resource.class.getMethod("noAnnotation"));

    assertThat(sampler.trySample(new ContainerAdapter(), request))
        .isFalse();
  }
}
