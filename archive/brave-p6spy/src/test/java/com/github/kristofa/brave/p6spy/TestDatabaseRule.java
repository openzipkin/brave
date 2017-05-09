package com.github.kristofa.brave.p6spy;

import org.junit.rules.ExternalResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

public class TestDatabaseRule extends ExternalResource {

    /**
     * This connection string adds 'p6spy' to the normal connection string for derby to active the p6spy proxy.
     */
    private static final String URL = "jdbc:p6spy:derby:memory:p6spy;create=true";
    private static final String USERNAME = "whatever";
    private static final String PASSWORD = "";

    private String url;
    private String username;
    private String password;
    private Connection connection;

    public TestDatabaseRule() {
        url = URL;
        username = USERNAME;
        password = PASSWORD;
    }

    public TestDatabaseRule(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public void executeStatement(SQLExceptionalConsumer<Statement> statementConsumer) {
        try {
            try (Statement statement = connection.createStatement()) {
                statementConsumer.accept(statement);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void executePreparedStatement(SQLExceptionalConsumer<PreparedStatement> statementConsumer, String sql) {
        try {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statementConsumer.accept(statement);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void before() throws Throwable {
        DriverManager.getDriver(url);
        connection = DriverManager.getConnection(url, username, password);

        String nativeUrl = url.replace("jdbc:p6spy:", "jdbc:");
        setupTestData(new DriverManagerDataSource(nativeUrl, username, password));
    }

    private static void setupTestData(final DataSource dataSource) throws LiquibaseException {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setChangeLog("classpath:liquibase.xml");
        liquibase.setDataSource(dataSource);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.setDropFirst(true);
        liquibase.afterPropertiesSet();
    }

    interface SQLExceptionalConsumer<T extends Statement> {
        void accept(T t) throws SQLException;
    }

}
