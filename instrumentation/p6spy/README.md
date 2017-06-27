# brave-instrumentation-p6spy
This includes a tracing event listener for [P6Spy](https://github.com/p6spy/p6spy) (a proxy for calls to your JDBC driver).
It reports to Zipkin how long each statement takes, along with relevant tags like the query.

P6Spy requires a `spy.properties` in your application classpath
(ex `src/main/resources`). `brave.p6spy.TracingP6Factory` must be in the
`modulelist` to enable tracing.

```
modulelist=brave.p6spy.TracingP6Factory
url=jdbc:p6spy:derby:memory:p6spy;create=true
```

By default the service name corresponding to your database is the name
of the database, but you can add another property to `spy.properties`
named `remoteServiceName` to customise it.

The current tracing component is used at runtime. Until you have
instantiated `brave.Tracing`, no traces will appear.
