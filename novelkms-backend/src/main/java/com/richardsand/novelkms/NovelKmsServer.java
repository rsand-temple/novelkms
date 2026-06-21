package com.richardsand.novelkms;

import java.sql.SQLException;
import java.time.Duration;

import org.apache.commons.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.richardsand.novelkms.auth.AuthenticationFilter;
import com.richardsand.novelkms.auth.OAuthService;
import com.richardsand.novelkms.auth.SessionService;
import com.richardsand.novelkms.auth.TenantAuthorizationFilter;
import com.richardsand.novelkms.dao.AuthDao;
import com.richardsand.novelkms.dao.BookDao;
import com.richardsand.novelkms.dao.ChapterDao;
import com.richardsand.novelkms.dao.CodexCategoryDao;
import com.richardsand.novelkms.dao.CodexDao;
import com.richardsand.novelkms.dao.PartDao;
import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.TemplateDao;
import com.richardsand.novelkms.dao.TenantAccessDao;
import com.richardsand.novelkms.dao.UserStyleDao;
import com.richardsand.novelkms.dropwizard.health.DataSourceHealthCheck;
import com.richardsand.novelkms.resource.AuthResource;
import com.richardsand.novelkms.resource.BookResource;
import com.richardsand.novelkms.resource.ChapterResource;
import com.richardsand.novelkms.resource.CodexResource;
import com.richardsand.novelkms.resource.ExportResource;
import com.richardsand.novelkms.resource.ImportResource;
import com.richardsand.novelkms.resource.PartResource;
import com.richardsand.novelkms.resource.ProjectResource;
import com.richardsand.novelkms.resource.SceneResource;
import com.richardsand.novelkms.resource.StyleResource;
import com.richardsand.novelkms.resource.TemplateResource;
import com.richardsand.novelkms.service.ExportService;
import com.richardsand.novelkms.service.ImportService;

import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

public class NovelKmsServer extends Application<NovelKmsConfig> {
    private static final Logger          logger     = LoggerFactory.getLogger(NovelKmsServer.class);
    private boolean               isPostgres = false;
    private final BasicDataSource ds         = new BasicDataSource();

    @Override
    public void initialize(Bootstrap<NovelKmsConfig> bootstrap) {
        bootstrap.setConfigurationSourceProvider(
                new Jinja2ConfigurationSourceProvider(bootstrap.getConfigurationSourceProvider()));

        bootstrap.addBundle(
                new AssetsBundle(
                        "/webapp",
                        "/",
                        "index.html"));
    }

    @Override
    public void run(NovelKmsConfig config, Environment env) throws Exception {
        String jdbcUrl   = config.getDatabase().url;
        String adminUser = config.getDatabase().adminUser;
        String adminPwd  = config.getDatabase().adminPwd;

        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:")) {
            logger.info("Loading POSTGRESQL driver");
            ds.setDriverClassName("org.postgresql.Driver");
        } else if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:h2:")) {
            logger.info("Loading H2 driver");
            ds.setDriverClassName("org.h2.Driver");
        }

        logger.info("Database URL: {}", jdbcUrl);
        ds.setUrl(jdbcUrl);
        ds.setUsername(adminUser);
        ds.setPassword(adminPwd);
        ds.setMinIdle(1);
        ds.setMaxIdle(5);
        ds.setMaxOpenPreparedStatements(100);
        ds.setDefaultAutoCommit(true);
        ds.setMaxTotal(15);
        ds.setMaxWait(Duration.ofMillis(10000));
        ds.setFastFailValidation(true);
        ds.setRemoveAbandonedOnBorrow(true);
        ds.setRemoveAbandonedTimeout(Duration.ofSeconds(30));
        ds.setLogAbandoned(true);

        try {
            isPostgres = ds.getConnection().getMetaData().getDatabaseProductName()
                    .toLowerCase().contains("postgres");
        } catch (SQLException e) {
            isPostgres = false;
        }

        Flyway.configure()
                .dataSource(jdbcUrl, adminUser, adminPwd)
                .locations("classpath:db/migration/" + (isPostgres ? "postgresql" : "h2"))
                .load()
                .migrate();

        ProjectDao       projectDao       = new ProjectDao(ds);
        BookDao          bookDao          = new BookDao(ds);
        PartDao          partDao          = new PartDao(ds);
        ChapterDao       chapterDao       = new ChapterDao(ds);
        CodexDao         codexDao         = new CodexDao(ds);
        CodexCategoryDao codexCategoryDao = new CodexCategoryDao(ds);
        SceneDao         sceneDao         = new SceneDao(ds);
        TemplateDao      templateDao      = new TemplateDao(ds);
        UserStyleDao     userStyleDao     = new UserStyleDao(ds);
        AuthDao          authDao          = new AuthDao(ds);
        TenantAccessDao  tenantAccessDao  = new TenantAccessDao(ds);

        ImportService  importService  = new ImportService(bookDao, partDao, chapterDao, sceneDao, projectDao);
        ExportService  exportService  = new ExportService(bookDao, partDao, chapterDao, sceneDao, projectDao, templateDao);
        SessionService sessionService = new SessionService(authDao, config.getAuth());
        OAuthService   oauthService   = new OAuthService(config.getAuth(), authDao);

        env.lifecycle().manage(new io.dropwizard.lifecycle.Managed() {
            @Override
            public void start() {
            }

            @Override
            public void stop() throws Exception {
                oauthService.close();
            }
        });

        env.jersey().register(MultiPartFeature.class);
        env.jersey().register(AuthResource.class);
        env.jersey().register(AuthenticationFilter.class);
        env.jersey().register(TenantAuthorizationFilter.class);
        env.jersey().register(BookResource.class);
        env.jersey().register(ChapterResource.class);
        env.jersey().register(CodexResource.class);
        env.jersey().register(ExportResource.class);
        env.jersey().register(ImportResource.class);
        env.jersey().register(PartResource.class);
        env.jersey().register(ProjectResource.class);
        env.jersey().register(SceneResource.class);
        env.jersey().register(TemplateResource.class);
        env.jersey().register(StyleResource.class);

        ObjectMapper mapper = env.getObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        env.jersey().register(new org.glassfish.hk2.utilities.binding.AbstractBinder() {
            @Override
            protected void configure() {
                bind(mapper).to(ObjectMapper.class);
                bind(config).to(NovelKmsConfig.class);
                bind(projectDao).to(ProjectDao.class);
                bind(bookDao).to(BookDao.class);
                bind(partDao).to(PartDao.class);
                bind(chapterDao).to(ChapterDao.class);
                bind(codexDao).to(CodexDao.class);
                bind(codexCategoryDao).to(CodexCategoryDao.class);
                bind(sceneDao).to(SceneDao.class);
                bind(templateDao).to(TemplateDao.class);
                bind(userStyleDao).to(UserStyleDao.class);
                bind(authDao).to(AuthDao.class);
                bind(tenantAccessDao).to(TenantAccessDao.class);
                bind(importService).to(ImportService.class);
                bind(exportService).to(ExportService.class);
                bind(sessionService).to(SessionService.class);
                bind(oauthService).to(OAuthService.class);
            }
        });

        env.healthChecks().register("database", new DataSourceHealthCheck(ds));
        logBuildInfo();
    }

    private void logBuildInfo() {
        try (var in = getClass().getClassLoader().getResourceAsStream("build.properties")) {
            if (in != null) {
                var props = new java.util.Properties();
                props.load(in);
                logger.info("NovelKMS Version {} Build {}",
                        props.getProperty("app.version", "unknown"),
                        props.getProperty("build.number", "unknown"));
            }
        } catch (Exception e) {
            logger.warn("Could not read build.properties", e);
        }
    }

    public static void main(String[] args) throws Exception {
        new NovelKmsServer().run(args);
    }
}
