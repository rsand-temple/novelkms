package com.richardsand.novelkms;


import java.sql.SQLException;
import java.time.Duration;

import org.apache.commons.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.richardsand.novelkms.dropwizard.health.DataSourceHealthCheck;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

public class NovelKmsServer extends Application<NovelKmsConfig> {
    Logger logger = LoggerFactory.getLogger(getClass());

    boolean         isPostgres = false;
    BasicDataSource ds         = new BasicDataSource();

    @Override
    public void initialize(Bootstrap<NovelKmsConfig> bootstrap) {
    }

    @Override
    public void run(NovelKmsConfig config, Environment env) throws Exception {
        String jdbcUrl   = config.getDatabase().url;
        String adminUser = config.getDatabase().adminUser;
        String adminPwd  = config.getDatabase().adminPwd;

        // Optionally set driver class if you like (BasicDataSource can usually infer from URL)
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:")) {
            logger.info("Loading POSTGRESQL driver");
            ds.setDriverClassName("org.postgresql.Driver");
        } else if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:h2:")) {
            logger.info("Loading H2 driver");
            ds.setDriverClassName("org.h2.Driver");
        }

        logger.debug("URL {}", jdbcUrl);
        ds.setUrl(jdbcUrl);
        ds.setUsername(adminUser);
        ds.setPassword(adminPwd);
        ds.setMinIdle(1);
        ds.setMaxIdle(5);
        ds.setMaxOpenPreparedStatements(100);
        ds.setDefaultAutoCommit(true);
        ds.setMaxTotal(15);
        ds.setMaxWait(Duration.ofMillis(10000)); // fail fast instead of hanging
        ds.setFastFailValidation(true);
        ds.setRemoveAbandonedOnBorrow(true);
        ds.setRemoveAbandonedTimeout(Duration.ofSeconds(30));
        ds.setLogAbandoned(true);

        try {
            String product = ds.getConnection().getMetaData().getDatabaseProductName();
            isPostgres = product != null && product.toLowerCase().contains("postgres");
        } catch (SQLException e) {
            isPostgres = false;
        }

        // Flyway migration, if necessary
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, adminUser, adminPwd)
                .locations("classpath:db/migration/" + ((isPostgres) ? "postgresql" : "h2"))
                .load();
        flyway.migrate();

        // DAOs

        // Resources

        // ObjectMapper
        ObjectMapper mapper = env.getObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Schedule jobs from config

        // Services

        // Make classes injectable to resources with HK2
        env.jersey().register(new org.glassfish.hk2.utilities.binding.AbstractBinder() {
            @Override
            protected void configure() {
                bind(mapper).to(ObjectMapper.class);
                bind(config).to(NovelKmsConfig.class);
            }
        });
        
        // Health checks
        env.healthChecks().register("database", new DataSourceHealthCheck(ds));
    }

    public static void main(String[] args) throws Exception {
        new NovelKmsServer().run(args);
    }
}