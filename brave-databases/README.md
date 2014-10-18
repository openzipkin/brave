# brave-databases #

The brave-databases module includes classes which will record the time taken to execute SQL queries
as well as the query that was executed.

## MySQL ##

For a MySQL database, append `?statementInterceptors=com.github.kristofa.brave.db.MySQLStatementInterceptor`
to the end of the JDBC connection string and then make sure that the `MySQLStatementInterceptorManagementBean`
class is used to inject the `ClientTracer` into the interceptor.

