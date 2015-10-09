# brave-mysql #

The brave-mysql module includes classes which will record the time taken to execute SQL queries
as well as the query that was executed for the MySQL database.

## Using ##

For a MySQL database, append `?statementInterceptors=com.github.kristofa.brave.mysql.MySQLStatementInterceptor`
to the end of the JDBC connection string and then make sure that the `MySQLStatementInterceptorManagementBean`
class is used to inject the `ClientTracer` into the interceptor.
