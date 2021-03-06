/*
 * Copyright (c) 2015, Haiyang Li. All rights reserved.
 */

package com.landawn.abacus.dataSource;

import static com.landawn.abacus.dataSource.DataSourceConfiguration.DRIVER;
import static com.landawn.abacus.dataSource.DataSourceConfiguration.JNDI_NAME;
import static com.landawn.abacus.dataSource.DataSourceConfiguration.LIVE_TIME;
import static com.landawn.abacus.dataSource.DataSourceConfiguration.MAX_ACTIVE;
import static com.landawn.abacus.dataSource.DataSourceConfiguration.MAX_IDLE_TIME;
import static com.landawn.abacus.dataSource.DataSourceConfiguration.MIN_IDLE;
import static com.landawn.abacus.dataSource.DataSourceConfiguration.PASSWORD;
import static com.landawn.abacus.dataSource.DataSourceConfiguration.URL;
import static com.landawn.abacus.dataSource.DataSourceConfiguration.USER;
import static com.landawn.abacus.dataSource.DataSourceConfiguration.VALIDATION_QUERY;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import com.landawn.abacus.exception.AbacusException;
import com.landawn.abacus.exception.UncheckedSQLException;
import com.landawn.abacus.logging.Logger;
import com.landawn.abacus.logging.LoggerFactory;

/**
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 */
class HikariConnectionManager extends AbstractConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(HikariConnectionManager.class);

    private final com.zaxxer.hikari.HikariDataSource ds;

    public HikariConnectionManager(Map<String, ?> props) {
        super(props);

        if (properties.containsKey(JNDI_NAME)) {
            throw new UnsupportedOperationException(); // TODO
        } else {
            final com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
            config.setDriverClassName(properties.get(DRIVER));
            config.setJdbcUrl(properties.get(URL));
            config.setUsername(properties.get(USER));
            config.setPassword(properties.get(PASSWORD));

            config.setMinimumIdle(Integer.valueOf(properties.get(MIN_IDLE)));
            config.setMaximumPoolSize(Integer.valueOf(properties.get(MAX_ACTIVE)));
            config.setIdleTimeout(Integer.valueOf(properties.get(MAX_IDLE_TIME)));
            config.setMaxLifetime(Integer.valueOf(properties.get(LIVE_TIME)));
            config.setConnectionTestQuery(properties.get(VALIDATION_QUERY));

            config.setDataSourceProperties(connectionProperties);

            ds = new com.zaxxer.hikari.HikariDataSource(config);
        }
    }

    @Override
    public int getMaxActive() {
        return ds.getMaximumPoolSize();
    }

    @Override
    public int getNumActive() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Connection getConnection() {
        try {
            return ds.getConnection();
        } catch (SQLException e) {
            String msg = AbacusException.getErrorMsg(e);
            logger.warn(msg);
            throw new UncheckedSQLException(msg, e);
        }
    }

    @Override
    public void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                String msg = AbacusException.getErrorMsg(e);
                logger.warn(msg);
                throw new UncheckedSQLException(msg, e);
            }
        }
    }

    @Override
    public void detroyConnection(Connection conn) {
        closeConnection(conn);
    }

    @Override
    public void close() {
        if (ds != null) {
            ds.close();
        }
    }
}
