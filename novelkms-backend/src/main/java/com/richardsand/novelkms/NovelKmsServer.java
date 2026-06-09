package com.richardsand.novelkms;

import java.sql.SQLException;
import java.time.Duration;

import org.apache.commons.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.richardsand.novelkms.dao.BookDao;
import com.richardsand.novelkms.dao.ChapterDao;
import com.richardsand.novelkms.dao.PartDao;
import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dropwizard.health.DataSourceHealthCheck;
import com.richardsand.novelkms.resource.BookResource;
import com.richardsand.novelkms.resource.ChapterResource;
import com.richardsand.novelkms.resource.PartResource;
import com.richardsand.novelkms.resource.ProjectResource;
import com.richardsand.novelkms.resource.SceneResource;

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
        ProjectDao projectDao = new ProjectDao(ds);
        BookDao    bookDao    = new BookDao(ds);
        PartDao    partDao    = new PartDao(ds);
        ChapterDao chapterDao = new ChapterDao(ds);
        SceneDao   sceneDao   = new SceneDao(ds);
        
        // Resources
        env.jersey().register(BookResource.class);
        env.jersey().register(ChapterResource.class);
        env.jersey().register(PartResource.class);
        env.jersey().register(ProjectResource.class);
        env.jersey().register(SceneResource.class);

        // ObjectMapper
        ObjectMapper mapper = env.getObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Make classes injectable to resources with HK2
        env.jersey().register(new org.glassfish.hk2.utilities.binding.AbstractBinder() {
            @Override
            protected void configure() {
                bind(mapper).to(ObjectMapper.class);
                bind(config).to(NovelKmsConfig.class);
                bind(projectDao).to(ProjectDao.class);
                bind(bookDao).to(BookDao.class);
                bind(partDao).to(PartDao.class);
                bind(chapterDao).to(ChapterDao.class);
                bind(sceneDao).to(SceneDao.class);            }
        });

        // Health checks
        env.healthChecks().register("database", new DataSourceHealthCheck(ds));
    }

    public static void main(String[] args) throws Exception {
        new NovelKmsServer().run(args);
    }
}