package brave.jaxrs2;

import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ContainerAdapterTest {
  ContainerAdapter adapter = new ContainerAdapter();
  @Mock ContainerRequestContext request;
  @Mock ResourceInfo info;

  @Test public void resourceInfo_requiresContainerRequest() {
    assertThat(adapter.resourceInfo(new Object()))
        .isNull();
  }

  @Test public void resourceInfo_returnsResourceInfo() {
    when(request.getProperty("javax.ws.rs.container.ResourceInfo")).thenReturn(info);

    assertThat(adapter.resourceInfo(request))
        .isEqualTo(info);
  }

  @Test public void resourceClass_requiresContainerRequest() {
    assertThat(adapter.resourceClass(new Object()))
        .isNull();
  }

  @Test public void resourceClass_returnsClassFromResourceInfo() {
    when(request.getProperty("javax.ws.rs.container.ResourceInfo")).thenReturn(info);
    when(info.getResourceClass()).thenReturn((Class) String.class);

    assertThat(adapter.resourceClass(request))
        .isEqualTo(String.class);
  }

  @Test public void resourceMethod_requiresContainerRequest() {
    assertThat(adapter.resourceMethod(new Object()))
        .isNull();
  }

  @Test public void resourceMethod_returnsMethodFromResourceInfo() throws Exception {
    Method method = String.class.getMethod("toString");
    when(request.getProperty("javax.ws.rs.container.ResourceInfo")).thenReturn(info);
    when(info.getResourceMethod()).thenReturn(method);

    assertThat(adapter.resourceMethod(request))
        .isEqualTo(method);
  }
}
