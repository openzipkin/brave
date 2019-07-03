# brave-context-slf4j
This adds trace and span IDs to the SLF4J Mapped Diagnostic Context (MDC)
so that you can search or aggregate logs accordingly.

To enable this, configure `brave.Tracing` with `MDCScopeDecorator`
like so:

```java
tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
       .addScopeDecorator(MDCScopeDecorator.create())
       .build()
    )
    ...
    .build();
```

Then, in your log configuration, you can use `traceId`, `parentId`, `spanId` and `sampled`.

Here's an example logback configuration:

```xml
<pattern>%d [%X{traceId}/%X{spanId}] [%thread] %-5level %logger{36} - %msg%n</pattern>
```

When a trace is in progress, it would log statements like this:
```
2017-05-02 23:36:04,789 [fcd015bf6f8b05ba/fcd015bf6f8b05ba] [main] INFO  c.a.FooController - I got here!
```

Users could then copy/paste the trace ID into the zipkin UI, or use log
correlation to further debug a problem.
