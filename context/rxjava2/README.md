# brave-context-rxjava2
`CurrentTraceContextAssemblyTracking` prevents traces from breaking
during RxJava operations by scoping trace context that existed
at assembly time around callbacks or computation of new values.

The design of this library borrows heavily from https://github.com/akaita/RxJava2Debug and https://github.com/akarnokd/RxJava2Extensions

To set this up, create `CurrentTraceContextAssemblyTracking` using the
current trace context provided by your `Tracing` component.

```java
contextTracking = CurrentTraceContextAssemblyTracking.create(
  tracing.currentTraceContext()
);
```

After your application-specific changes to `RxJavaPlugins`, enable trace
context tracking like so:

```java
contextTracking.enable();
```

Or, if you want to be able to restore any preceding hooks, enable like
this:
```java
SavedHooks hooks = contextTracking.enableAndChain();

// then, later you can restore like this
hooks.restore();
```

## Notes on Fusion
Fuseable types, such as `ConditionalSubscriber` and `ScalarCallable` are
not currently supported. Use of these hooks will mask that functionality.

This is done because the types are internal and subject to code drift.
