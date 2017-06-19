package brave.http;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpRuleSamplerTest {
  @Mock HttpClientAdapter<Object, Object> adapter;
  Object request = new Object();

  @Test public void onPath() {
    HttpSampler sampler = HttpRuleSampler.newBuilder()
        .addRule(null, "/foo", 1.0f)
        .build();

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/foo");

    assertThat(sampler.trySample(adapter, request))
        .isTrue();
  }

  @Test public void onPath_sampled() {
    HttpSampler sampler = HttpRuleSampler.newBuilder()
        .addRule(null, "/foo", 0.0f)
        .build();

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/foo");

    assertThat(sampler.trySample(adapter, request))
        .isFalse();
  }

  @Test public void onPath_sampled_prefix() {
    HttpSampler sampler = HttpRuleSampler.newBuilder()
        .addRule(null, "/foo", 0.0f)
        .build();

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/foo/abcd");

    assertThat(sampler.trySample(adapter, request))
        .isFalse();
  }

  @Test public void onPath_doesntMatch() {
    HttpSampler sampler = HttpRuleSampler.newBuilder()
        .addRule(null, "/foo", 0.0f)
        .build();

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/bar");

    assertThat(sampler.trySample(adapter, request))
        .isNull();
  }

  @Test public void onMethodAndPath_sampled() {
    HttpSampler sampler = HttpRuleSampler.newBuilder()
        .addRule("GET", "/foo", 1.0f)
        .build();

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/foo");

    assertThat(sampler.trySample(adapter, request))
        .isTrue();
  }

  @Test public void onMethodAndPath_sampled_prefix() {
    HttpSampler sampler = HttpRuleSampler.newBuilder()
        .addRule("GET", "/foo", 1.0f)
        .build();

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/foo/abcd");

    assertThat(sampler.trySample(adapter, request))
        .isTrue();
  }

  @Test public void onMethodAndPath_unsampled() {
    HttpSampler sampler = HttpRuleSampler.newBuilder()
        .addRule("GET", "/foo", 0.0f)
        .build();

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/foo");

    assertThat(sampler.trySample(adapter, request))
        .isFalse();
  }

  @Test public void onMethodAndPath_doesntMatch_method() {
    HttpSampler sampler = HttpRuleSampler.newBuilder()
        .addRule("GET", "/foo", 0.0f)
        .build();

    when(adapter.method(request)).thenReturn("POST");
    when(adapter.path(request)).thenReturn("/foo");

    assertThat(sampler.trySample(adapter, request))
        .isNull();
  }

  @Test public void onMethodAndPath_doesntMatch_path() {
    HttpSampler sampler = HttpRuleSampler.newBuilder()
        .addRule("GET", "/foo", 0.0f)
        .build();

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/bar");

    assertThat(sampler.trySample(adapter, request))
        .isNull();
  }

  @Test public void nullOnParseFailure() {
    HttpSampler sampler = HttpRuleSampler.newBuilder()
        .addRule("GET", "/foo", 0.0f)
        .build();

    // not setting up mocks means they return null which is like a parse fail
    assertThat(sampler.trySample(adapter, request))
        .isNull();
  }
}
