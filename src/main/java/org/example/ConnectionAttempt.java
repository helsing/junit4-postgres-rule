package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.Callable;

public class ConnectionAttempt implements Callable<Boolean> {
    private final String connectionUrl;
    private final Properties connectionProperties;

    public ConnectionAttempt(String connectionUrl, Properties connectionProperties) {
        this.connectionUrl = connectionUrl;
        this.connectionProperties = connectionProperties;
    }

    @Override
    public Boolean call() throws Exception {
        try (Connection connection = DriverManager.getConnection(connectionUrl, connectionProperties)) {
            return connection != null;
        } catch (SQLException e) {
            return false;
        }
    }
}
