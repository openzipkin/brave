package brave.sampler;

import brave.internal.Nullable;
import brave.propagation.SamplingFlags;
import java.util.List;

/**
 * This is an implementation of how to decide whether to trace a request using ordered rules. For
 * example, you could write rules to look at an HTTP method and path, or a RabbitMQ routing key and
 * queue name.
 *
 * <p>This looks at runtime parameters to see if they {@link Rule#matches(Object) match} a rule. If
 * all calls to a java method should have the same sample rate, consider {@link DeclarativeSampler}
 * instead.
 *
 * @param <P> The type that encloses parameters associated with a sample rate. For example, this
 * could be a pair of http and method..
 */
public final class ParameterizedSampler<P> {
  public static <P> ParameterizedSampler<P> create(List<? extends Rule<P>> rules) {
    if (rules == null) throw new NullPointerException("rules == null");
    return new ParameterizedSampler<>(rules);
  }

  public static abstract class Rule<P> {
    final Sampler sampler;

    /**
     * @param rate percentage of requests to start traces for. 1.0 is 100%
     */
    protected Rule(float rate) {
      sampler = CountingSampler.create(rate);
    }

    /** Returns true if this rule matches the input parameters */
    public abstract boolean matches(P parameters);

    SamplingFlags isSampled() {
      return sampler.isSampled(0L) // counting sampler ignores the input
          ? SamplingFlags.SAMPLED
          : SamplingFlags.NOT_SAMPLED;
    }
  }

  final List<? extends Rule<P>> rules;

  ParameterizedSampler(List<? extends Rule<P>> rules) {
    this.rules = rules;
  }

  public SamplingFlags sample(@Nullable P parameters) {
    if (parameters == null) return SamplingFlags.EMPTY;
    for (Rule<P> rule : rules) {
      if (rule.matches(parameters)) return rule.isSampled();
    }
    return SamplingFlags.EMPTY;
  }
}
