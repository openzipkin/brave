package brave.jaxrs2.features.sampling;

import brave.http.HttpAdapter;
import brave.http.HttpSampler;
import brave.jaxrs2.ContainerAdapter;
import brave.sampler.DeclarativeSampler;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

@Retention(RetentionPolicy.RUNTIME)
public @interface Traced {
  float sampleRate() default 1.0f;

  boolean enabled() default true;

  final class Sampler extends HttpSampler {
    final DeclarativeSampler<Traced> sampler;
    final HttpSampler fallback;

    Sampler(HttpSampler fallback) {
      this.sampler = DeclarativeSampler.create(t -> t.enabled() ? t.sampleRate() : 0.0f);
      this.fallback = fallback;
    }

    @Nullable @Override
    public final <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
      if (adapter instanceof ContainerAdapter) {
        Method method = ((ContainerAdapter) adapter).resourceMethod(request);
        Traced traced = method != null ? method.getAnnotation(Traced.class) : null;
        if (traced != null) return sampler.sample(traced).sampled();
      }
      return fallback.trySample(adapter, request);
    }
  }
}
