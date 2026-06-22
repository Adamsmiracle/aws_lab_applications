package com.example.demo;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Builds the PostgreSQL DataSource from the resolved AppSettings (endpoint +
 * db name from SSM, credentials from Secrets Manager) rather than static
 * application.properties, since those values are environment-specific.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(AppSettings settings) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:postgresql://" + settings.getDbEndpoint() + ":5432/" + settings.getDbName());
        cfg.setUsername(settings.getDbUsername());
        cfg.setPassword(settings.getDbPassword());
        cfg.setMaximumPoolSize(5);
        cfg.setPoolName("photo-gallery-pool");
        return new HikariDataSource(cfg);
    }
}
