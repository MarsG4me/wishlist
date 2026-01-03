package de.marsg.wishlist.wishlist.configs;

import javax.sql.DataSource;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.DependsOn;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfig {

    private final ConfigMgr configMgr;

    public DataSourceConfig(ConfigMgr configMgr) {
        this.configMgr = configMgr;
    }

    @Bean
    @Primary
    @DependsOn("configMgr")
    public DataSource dataSource() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(configMgr.getProperty("db.jdbc_url"));
            config.setUsername(configMgr.getProperty("db.username"));
            config.setPassword(configMgr.getProperty("db.password"));
            config.setMinimumIdle(1);
            config.setMaximumPoolSize(10);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(60000);
            return new HikariDataSource(config);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create DB pool", e);
        }
    }

}
