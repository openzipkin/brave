# brave-instrumentation-jdbi3

This includes a JDBI 3 plugin that will report to Zipkin how long each
statement takes, along with relevant tags like the query.

To use it, call the `installPlugin` on the `Jdbi` instance you want to
instrument, or add the statement context listener manually like so:
```
jdbi.getConfig(SqlStatements.class)
  .addContextListener(new BraveStatementContextListener(tracing));
```

The remote service name of the span is set to the hostname and port number of
the database server, if available, and the URL scheme if not. If the database
URL format allows it, you can add the `zipkinServiceName` query parameter to
override the remote service name.

Bind variable values are not included in the traces, only the SQL statement
with placeholders.