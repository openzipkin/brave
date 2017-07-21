# brave-instrumentation-mysql6

This includes a mysql-connector-java 6+ statement interceptor that will report to Zipkin
how long each statement takes, along with relevant tags like the query.

To use it, append `?statementInterceptors=brave.mysql6.TracingStatementInterceptor`
to the end of the connection url.

By default the service name corresponding to your database uses the format
`mysql-${database}`, but you can append another property `zipkinServiceName` to customise it.

`?statementInterceptors=brave.mysql6.TracingStatementInterceptor&zipkinServiceName=myDatabaseService`

The current tracing component is used at runtime. Until you have
instantiated `brave.Tracing`, no traces will appear.
