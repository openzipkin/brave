# brave-mysql #

The brave-mysql module includes classes which will record the time taken to execute SQL queries
as well as the query that was executed for the MySQL database.

## Using ##

1. Inject `ClientTracer` into `MySQLStatementInterceptorManagementBean`. E.g.

```java
Brave brave = new Brave.Builder("myService").build();
new MySQLStatementInterceptorManagementBean(brave.clientTracer());
```

or Spring IoC

```xml
<bean id="braveBuilder" class="com.github.kristofa.brave.Brave.Builder">
    <contructor-arg value="myService" />
</bean>
<bean id="brave" factory-bean="braveBuilder" factory-method="build" />
<bean class="com.github.kristofa.brave.mysql.MySQLStatementInterceptorManagementBean"
    destroy-method="close">
    <constructor-arg value="#{brave.clientTracer()}" />
</bean>
```

2. Append
`?statementInterceptors=com.github.kristofa.brave.mysql.MySQLStatementInterceptor` to the end of
the JDBC connection string. By default the service name of this span will use the format
`mysql-${database}`, but you can append another property `zipkinServiceName` to customise it.

`?statementInterceptors=com.github.kristofa.brave.mysql.MySQLStatementInterceptor&zipkinServiceName=myDatabaseService`

**Note**: Here the _myDatabaseService_ differs from the above _myService_, the former one is the
Java application service name, but the latter one is the service name of your MySQL database.
