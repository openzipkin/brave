# brave-instrumentation-mysql

This includes a MySQL statement interceptor that will report to Zipkin
how long each statement takes, along with relevant tags like the query.

To use it, append `?statementInterceptors=brave.mysql.TracingStatementInterceptor`
to the end of the connection url.

By default the service name corresponding to your database uses the format
`mysql-${database}`, but you can append another property `zipkinServiceName` to customise it.

`?statementInterceptors=brave.mysql.TracingStatementInterceptor&zipkinServiceName=myDatabaseService`

The current tracing component is used at runtime. Until you have
instantiated `brave.Tracing`, no traces will appear.
