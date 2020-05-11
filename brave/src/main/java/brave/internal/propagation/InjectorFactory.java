/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.internal.propagation;

import brave.Request;
import brave.propagation.Propagation;
import brave.propagation.Propagation.RemoteSetter;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class InjectorFactory {
  /** Like {@link TraceContext.Injector}, except the {@link Setter} is a parameter. */
  public interface InjectorFunction {
    InjectorFunction NOOP = new InjectorFunction() {
      @Override public List<String> keyNames() {
        return Collections.emptyList();
      }

      @Override public <R> void inject(Setter<R, String> setter, TraceContext context, R request) {
      }
    };

    /**
     * The distinct list of key names this can inject.
     *
     * @see Propagation#keys()
     */
    List<String> keyNames();

    <R> void inject(Setter<R, String> setter, TraceContext context, R request);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    InjectorFunction injectorFunction = InjectorFunction.NOOP;
    InjectorFunction clientInjectorFunction = InjectorFunction.NOOP;
    InjectorFunction producerInjectorFunction = InjectorFunction.NOOP;
    InjectorFunction consumerInjectorFunction = InjectorFunction.NOOP;

    public Builder injectorFunctions(InjectorFunction... injectorFunctions) {
      this.injectorFunction = injectorFunction(injectorFunctions);
      return this;
    }

    public Builder clientInjectorFunctions(InjectorFunction... injectorFunctions) {
      this.clientInjectorFunction = injectorFunction(injectorFunctions);
      return this;
    }

    public Builder producerInjectorFunctions(InjectorFunction... injectorFunctions) {
      this.producerInjectorFunction = injectorFunction(injectorFunctions);
      return this;
    }

    public Builder consumerInjectorFunctions(InjectorFunction... injectorFunctions) {
      this.consumerInjectorFunction = injectorFunction(injectorFunctions);
      return this;
    }

    public InjectorFactory build() {
      if (injectorFunction == InjectorFunction.NOOP) {
        throw new IllegalArgumentException("injectorFunction == NOOP");
      }
      if (clientInjectorFunction == InjectorFunction.NOOP) {
        throw new IllegalArgumentException("clientInjectorFunction == NOOP");
      }
      if (producerInjectorFunction == InjectorFunction.NOOP) {
        throw new IllegalArgumentException("producerInjectorFunction == NOOP");
      }
      if (consumerInjectorFunction == InjectorFunction.NOOP) {
        throw new IllegalArgumentException("consumerInjectorFunction == NOOP");
      }
      return new InjectorFactory(this);
    }

    Builder() {
    }
  }

  final InjectorFunction injectorFunction;
  final InjectorFunction clientInjectorFunction, producerInjectorFunction, consumerInjectorFunction;
  final List<String> keyNames;

  InjectorFactory(Builder builder) {
    injectorFunction = builder.injectorFunction;
    clientInjectorFunction = builder.clientInjectorFunction;
    producerInjectorFunction = builder.producerInjectorFunction;
    consumerInjectorFunction = builder.consumerInjectorFunction;
    Set<String> keyNames = new LinkedHashSet<>();
    // Add messaging first as their formats are likely the cheapest to extract
    keyNames.addAll(builder.consumerInjectorFunction.keyNames());
    keyNames.addAll(builder.producerInjectorFunction.keyNames());
    keyNames.addAll(builder.clientInjectorFunction.keyNames());
    keyNames.addAll(builder.injectorFunction.keyNames());
    this.keyNames = Collections.unmodifiableList(new ArrayList<>(keyNames));
  }

  /**
   * The distinct list of key names this can inject.
   *
   * @see Propagation#keys()
   */
  public List<String> keyNames() {
    return keyNames;
  }

  public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
    if (setter == null) throw new NullPointerException("setter == null");
    if (setter instanceof RemoteSetter) {
      RemoteSetter<?> remoteSetter = (RemoteSetter<?>) setter;
      switch (remoteSetter.spanKind()) {
        case CLIENT:
          return new Injector<>(setter, clientInjectorFunction);
        case PRODUCER:
          return new Injector<>(setter, producerInjectorFunction);
        case CONSUMER:
          return new Injector<>(setter, consumerInjectorFunction);
        default: // SERVER is nonsense as it cannot be injected
      }
    }
    return new DeferredInjector<>(setter, this);
  }

  @Override public int hashCode() {
    int h = 1000003;
    h ^= injectorFunction.hashCode();
    h *= 1000003;
    h ^= clientInjectorFunction.hashCode();
    h *= 1000003;
    h ^= producerInjectorFunction.hashCode();
    h *= 1000003;
    h ^= consumerInjectorFunction.hashCode();
    return h;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof InjectorFactory)) return false;
    InjectorFactory that = (InjectorFactory) o;
    return injectorFunction.equals(that.injectorFunction)
        && clientInjectorFunction.equals(that.clientInjectorFunction)
        && producerInjectorFunction.equals(that.producerInjectorFunction)
        && consumerInjectorFunction.equals(that.consumerInjectorFunction);
  }

  @Override public String toString() {
    return "InjectorFactory{injectorFunction=" + injectorFunction
        + ", clientInjectorFunction=" + clientInjectorFunction
        + ", producerInjectorFunction=" + producerInjectorFunction
        + ", consumerInjectorFunction=" + consumerInjectorFunction
        + "}";
  }

  static final class DeferredInjector<R> implements TraceContext.Injector<R> {
    final Setter<R, String> setter;
    final InjectorFactory injectorFactory;

    DeferredInjector(Setter<R, String> setter, InjectorFactory injectorFactory) {
      this.setter = setter;
      this.injectorFactory = injectorFactory;
    }

    @Override public void inject(TraceContext context, R request) {
      if (request instanceof Request) {
        switch (((Request) request).spanKind()) {
          case CLIENT:
            injectorFactory.clientInjectorFunction.inject(setter, context, request);
            return;
          case PRODUCER:
            injectorFactory.producerInjectorFunction.inject(setter, context, request);
            return;
          case CONSUMER:
            injectorFactory.consumerInjectorFunction.inject(setter, context, request);
          default: // SERVER is nonsense as it cannot be injected
        }
      }
      injectorFactory.injectorFunction.inject(setter, context, request);
    }

    @Override public int hashCode() {
      int h = 1000003;
      h ^= setter.hashCode();
      h *= 1000003;
      h ^= injectorFactory.hashCode();
      return h;
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof DeferredInjector)) return false;
      DeferredInjector<?> that = (DeferredInjector<?>) o;
      return setter.equals(that.setter) && injectorFactory.equals(that.injectorFactory);
    }

    @Override public String toString() {
      return "DeferredInjector{setter=" + setter + ", injectorFactory=" + injectorFactory + "}";
    }
  }

  static final class Injector<R> implements TraceContext.Injector<R> {
    final InjectorFunction injectorFunction;
    final Setter<R, String> setter;

    Injector(Setter<R, String> setter, InjectorFunction injectorFunction) {
      this.injectorFunction = injectorFunction;
      this.setter = setter;
    }

    @Override public void inject(TraceContext context, R request) {
      injectorFunction.inject(setter, context, request);
    }

    @Override public int hashCode() {
      int h = 1000003;
      h ^= setter.hashCode();
      h *= 1000003;
      h ^= injectorFunction.hashCode();
      return h;
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof Injector)) return false;
      Injector<?> that = (Injector<?>) o;
      return setter.equals(that.setter) && injectorFunction.equals(that.injectorFunction);
    }

    @Override public String toString() {
      return "Injector{setter=" + setter + ", injectorFunction=" + injectorFunction + "}";
    }
  }

  static InjectorFunction injectorFunction(InjectorFunction... injectorFunctions) {
    if (injectorFunctions == null) throw new NullPointerException("injectorFunctions == null");
    LinkedHashSet<InjectorFunction> injectorFunctionSet =
        new LinkedHashSet<>(Arrays.asList(injectorFunctions));
    injectorFunctionSet.remove(InjectorFunction.NOOP);
    if (injectorFunctionSet.isEmpty()) return InjectorFunction.NOOP;
    if (injectorFunctionSet.size() == 1) return injectorFunctionSet.iterator().next();
    return new CompositeInjectorFunction(injectorFunctionSet.toArray(new InjectorFunction[0]));
  }

  static final class CompositeInjectorFunction implements InjectorFunction {
    final InjectorFunction[] injectorFunctions; // Array ensures no iterators are created at runtime
    final List<String> keyNames;

    CompositeInjectorFunction(InjectorFunction[] injectorFunctions) {
      this.injectorFunctions = injectorFunctions;
      Set<String> keyNames = new LinkedHashSet<>();
      for (InjectorFunction injectorFunction : injectorFunctions) {
        keyNames.addAll(injectorFunction.keyNames());
      }
      this.keyNames = Collections.unmodifiableList(new ArrayList<>(keyNames));
    }

    @Override public List<String> keyNames() {
      return keyNames;
    }

    @Override public <R> void inject(Setter<R, String> setter, TraceContext context, R request) {
      for (InjectorFunction injectorFunction : injectorFunctions) {
        injectorFunction.inject(setter, context, request);
      }
    }

    @Override public int hashCode() {
      int h = 1000003;
      h ^= Arrays.hashCode(injectorFunctions);
      return h;
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof CompositeInjectorFunction)) return false;
      return Arrays.equals(injectorFunctions, ((CompositeInjectorFunction) o).injectorFunctions);
    }

    @Override public String toString() {
      return Arrays.toString(injectorFunctions);
    }
  }
}
