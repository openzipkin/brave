# brave-instrumentation-alibaba-druid

## Druid Filter

This is a tracing filter for  [Alibaba Druid 1.1.14 ](https://github.com/alibaba/druid)，support more databases(mysql、postgresql、oracle、Microsoft、DB2、sqlite etc.) to tracing sql query and can replace brave-instrumentation-mysql.

## Configuration

    <bean id="tracingStatementFilter" class="brave.druid.TracingStatementFilter">
	<property name="zipkinServiceName" value="dbServer" /></bean>

  	<bean id="dataSource"  class="com.alibaba.druid.pool.DruidDataSource"
       init-method="init" destroy-method="close">
      <property name="url" value="jdbc:derby:memory:spring-test;create=true" />
      <property name="initialSize" value="1" />
      <property name="maxActive" value="20" />
      <property name="proxyFilters">
          <list>
              <ref bean="tracingStatementFilter" />
          </list>
      </property>
  	</bean>

By default the service name corresponding to your database uses the format `${database}`