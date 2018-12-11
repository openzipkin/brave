# brave-context-jfr
This adds trace and span IDs to JDK Flight Recorder "Scope Events" so
that you can correlate with Zipkin UI or logs accordingly.

This currently requires JDK 11. JVMs that use this need to add the below
to their VM start arguments:
```bash
-XX:+UnlockCommercialFeatures -XX:+FlightRecorder
```

To view the flight recordings, you need [JDK Mission Control 7](http://jdk.java.net/jmc/).

With the above in place, you can configure `brave.Tracing` with
`JfrScopeDecorator` like so:

```java
tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
       .addScopeDecorator(JfrScopeDecorator.create())
       .build()
    )
    ...
    .build();
```

After a flight is recorded, you can look for "Zipkin/Scope Event" in the
Event browser like so:

<img width="1021" alt="flight recording" src="https://user-images.githubusercontent.com/64215/49773912-f0328b00-fd2d-11e8-9acd-26b82694aea9.png">

Users could then copy/paste the trace ID into the zipkin UI, or use log
correlation to further debug a problem.
