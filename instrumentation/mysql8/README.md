# brave-instrumentation-mysql8

This includes a mysql-connector-java 8+ query interceptor that will report to Zipkin
how long each query takes, along with relevant tags like the query.

To use it, append `?queryInterceptors=brave.mysql8.TracingQueryInterceptor`
to the end of the connection url.

It is also recommended to add the exception interceptor so errors are added to the span, e.g.,
`?queryInterceptors=brave.mysql8.TracingQueryInterceptor&exceptionInterceptors=brave.mysql8.TracingExceptionInterceptor`

By default the service name corresponding to your database uses the format
`mysql-${database}`, but you can append another property `zipkinServiceName` to customise it.

`?queryInterceptors=brave.mysql8.TracingQueryInterceptor&exceptionInterceptors=brave.mysql8.TracingExceptionInterceptor&zipkinServiceName=myDatabaseService`

The current tracing component is used at runtime. Until you have
instantiated `brave.Tracing`, no traces will appear.
