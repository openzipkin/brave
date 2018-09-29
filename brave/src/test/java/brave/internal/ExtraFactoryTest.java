package brave.internal;

import brave.ScopedSpan;
import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import java.util.List;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class ExtraFactoryTest<E, F extends ExtraFactory<E>> {

  protected F factory = newFactory();

  protected abstract F newFactory();

  protected final Propagation.Factory propagationFactory = new Propagation.Factory() {
    @Override public <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      return B3Propagation.FACTORY.create(keyFactory);
    }

    @Override public TraceContext decorate(TraceContext context) {
      return factory.decorate(context);
    }
  };

  protected TraceContext context = propagationFactory.decorate(TraceContext.newBuilder()
      .traceId(1L)
      .spanId(2L)
      .sampled(true)
      .build());

  @Test public void decorate_empty() {
    assertThat(factory.decorate(contextWithExtra(context, asList(1L))).extra())
        .containsExactly(1L, factory.create());
    assertThat(factory.decorate(contextWithExtra(context, asList(1L, 2L))).extra())
        .containsExactly(1L, 2L, factory.create());
    assertThat(factory.decorate(contextWithExtra(context, asList(factory.create()))).extra())
        .containsExactly(factory.create());
    assertThat(factory.decorate(contextWithExtra(context, asList(factory.create(), 1L))).extra())
        .containsExactly(factory.create(), 1L);
    assertThat(factory.decorate(contextWithExtra(context, asList(1L, factory.create()))).extra())
        .containsExactly(1L, factory.create());

    E claimedBySelf = factory.create();
    factory.tryToClaim(claimedBySelf, context.traceId(), context.spanId());
    assertThat(factory.decorate(contextWithExtra(context, asList(claimedBySelf))).extra())
        .containsExactly(factory.create());
    assertThat(factory.decorate(contextWithExtra(context, asList(claimedBySelf, 1L))).extra())
        .containsExactly(factory.create(), 1L);
    assertThat(factory.decorate(contextWithExtra(context, asList(1L, claimedBySelf))).extra())
        .containsExactly(1L, factory.create());

    E claimedByOther = factory.create();
    factory.tryToClaim(claimedBySelf, 99L, 99L);
    assertThat(factory.decorate(contextWithExtra(context, asList(claimedByOther))).extra())
        .containsExactly(factory.create());
    assertThat(factory.decorate(contextWithExtra(context, asList(claimedByOther, 1L))).extra())
        .containsExactly(factory.create(), 1L);
    assertThat(factory.decorate(contextWithExtra(context, asList(1L, claimedByOther))).extra())
        .containsExactly(1L, factory.create());
  }

  @Test public void idempotent() {
    List<Object> originalExtra = context.extra();
    assertThat(propagationFactory.decorate(context).extra())
        .isSameAs(originalExtra);
  }

  @Test public void toSpan_selfLinksContext() {
    try (Tracing t = Tracing.newBuilder().propagationFactory(propagationFactory).build()) {
      ScopedSpan parent = t.tracer().startScopedSpan("parent");
      try {
        E extra = (E) parent.context().extra().get(0);

        assertAssociatedWith(extra, parent.context().traceId(), parent.context().spanId());
      } finally {
        parent.finish();
      }
    }
  }

  protected abstract void assertAssociatedWith(E extra, long traceId, long spanId);

  static TraceContext contextWithExtra(TraceContext context, List<Object> extra) {
    return InternalPropagation.instance.withExtra(context, extra);
  }
}
