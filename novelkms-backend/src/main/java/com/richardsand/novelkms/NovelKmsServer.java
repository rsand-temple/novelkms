package com.richardsand.novelkms;

import java.util.EnumSet;

import org.apache.commons.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.richardsand.novelkms.ai.AiProviderRegistry;
import com.richardsand.novelkms.ai.impl.AnthropicProvider;
import com.richardsand.novelkms.ai.impl.GeminiProvider;
import com.richardsand.novelkms.ai.impl.OpenAiProvider;
import com.richardsand.novelkms.auth.AuthenticationFilter;
import com.richardsand.novelkms.auth.OAuthService;
import com.richardsand.novelkms.auth.SecretCipher;
import com.richardsand.novelkms.auth.SessionService;
import com.richardsand.novelkms.auth.SubscriptionAuthorizationFilter;
import com.richardsand.novelkms.auth.TenantAuthorizationFilter;
import com.richardsand.novelkms.dao.AccountDao;
import com.richardsand.novelkms.dao.ArchiveDao;
import com.richardsand.novelkms.dao.AuthDao;
import com.richardsand.novelkms.dao.EditorSettingsDao;
import com.richardsand.novelkms.dao.MemoryTemplateDao;
import com.richardsand.novelkms.dao.PageLayoutDao;
import com.richardsand.novelkms.dao.PartDao;
import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.StripeWebhookEventDao;
import com.richardsand.novelkms.dao.TemplateDao;
import com.richardsand.novelkms.dao.TenantAccessDao;
import com.richardsand.novelkms.dao.TrashDao;
import com.richardsand.novelkms.dao.admin.AdminAuditDao;
import com.richardsand.novelkms.dao.admin.AdminMetricsDao;
import com.richardsand.novelkms.dao.admin.AdminUserDao;
import com.richardsand.novelkms.dao.ai.AiCredentialDao;
import com.richardsand.novelkms.dao.ai.AiFormInstructionsDao;
import com.richardsand.novelkms.dao.ai.AiPromptTemplateDao;
import com.richardsand.novelkms.dao.ai.AiReviewDao;
import com.richardsand.novelkms.dao.artifact.ArtifactBlobDao;
import com.richardsand.novelkms.dao.artifact.ArtifactNodeDao;
import com.richardsand.novelkms.dao.book.BookDao;
import com.richardsand.novelkms.dao.book.BookOutlineDao;
import com.richardsand.novelkms.dao.book.BookSummaryDao;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.dao.chapter.ChapterEditorialDao;
import com.richardsand.novelkms.dao.chapter.ChapterMemoryDao;
import com.richardsand.novelkms.dao.chapter.ChapterSummaryDao;
import com.richardsand.novelkms.dao.codex.CodexCategoryDao;
import com.richardsand.novelkms.dao.codex.CodexDao;
import com.richardsand.novelkms.dao.codex.CodexTypeDao;
import com.richardsand.novelkms.dao.codex.CodexTypeFieldDao;
import com.richardsand.novelkms.dao.review.ContentReportDao;
import com.richardsand.novelkms.dao.review.HumanReviewDao;
import com.richardsand.novelkms.dao.review.ReviewMetricsDao;
import com.richardsand.novelkms.dao.review.ReviewProfileDao;
import com.richardsand.novelkms.dao.review.ReviewRequestDao;
import com.richardsand.novelkms.dao.review.ReviewSnapshotDao;
import com.richardsand.novelkms.dao.review.ReviewQueueDao;
import com.richardsand.novelkms.dao.review.UserBlockDao;
import com.richardsand.novelkms.dao.user.UserPreferenceDao;
import com.richardsand.novelkms.dao.user.UserStyleDao;
import com.richardsand.novelkms.dao.user.UserSubscriptionDao;
import com.richardsand.novelkms.dropwizard.health.DataSourceHealthCheck;
import com.richardsand.novelkms.dropwizard.health.StartStopNotifier;
import com.richardsand.novelkms.dropwizard.web.SpaFallbackFilter;
import com.richardsand.novelkms.resource.AccountResource;
import com.richardsand.novelkms.resource.ArchiveResource;
import com.richardsand.novelkms.resource.ArtifactResource;
import com.richardsand.novelkms.resource.AuthResource;
import com.richardsand.novelkms.resource.BillingResource;
import com.richardsand.novelkms.resource.BookOutlineResource;
import com.richardsand.novelkms.resource.BookResource;
import com.richardsand.novelkms.resource.ChapterMemoryResource;
import com.richardsand.novelkms.resource.ChapterResource;
import com.richardsand.novelkms.resource.EditorSettingsResource;
import com.richardsand.novelkms.resource.EditorialResource;
import com.richardsand.novelkms.resource.ExportResource;
import com.richardsand.novelkms.resource.ImportResource;
import com.richardsand.novelkms.resource.PageLayoutResource;
import com.richardsand.novelkms.resource.PartResource;
import com.richardsand.novelkms.resource.ProjectResource;
import com.richardsand.novelkms.resource.SceneResource;
import com.richardsand.novelkms.resource.StripeWebhookResource;
import com.richardsand.novelkms.resource.StyleResource;
import com.richardsand.novelkms.resource.SummaryResource;
import com.richardsand.novelkms.resource.ToolsResource;
import com.richardsand.novelkms.resource.TrashResource;
import com.richardsand.novelkms.resource.UserPreferenceResource;
import com.richardsand.novelkms.resource.admin.AdminAuditResource;
import com.richardsand.novelkms.resource.admin.AdminBillingResource;
import com.richardsand.novelkms.resource.admin.AdminMetricsResource;
import com.richardsand.novelkms.resource.admin.AdminModerationResource;
import com.richardsand.novelkms.resource.admin.AdminSystemResource;
import com.richardsand.novelkms.resource.admin.AdminUserResource;
import com.richardsand.novelkms.resource.ai.AiContextResource;
import com.richardsand.novelkms.resource.ai.AiCredentialResource;
import com.richardsand.novelkms.resource.ai.AiFormInstructionsResource;
import com.richardsand.novelkms.resource.ai.AiReviewResource;
import com.richardsand.novelkms.resource.codex.CodexEntryResource;
import com.richardsand.novelkms.resource.codex.CodexResource;
import com.richardsand.novelkms.resource.review.HumanReviewResource;
import com.richardsand.novelkms.resource.review.ReviewProfileResource;
import com.richardsand.novelkms.resource.review.ReviewPublishResource;
import com.richardsand.novelkms.resource.review.ReviewRequestResource;
import com.richardsand.novelkms.resource.review.ReviewQueueResource;
import com.richardsand.novelkms.resource.review.ReviewSafetyResource;
import com.richardsand.novelkms.resource.template.BookSummaryTemplateResource;
import com.richardsand.novelkms.resource.template.ChapterSummaryTemplateResource;
import com.richardsand.novelkms.resource.template.EditorialTemplateResource;
import com.richardsand.novelkms.resource.template.MemoryTemplateResource;
import com.richardsand.novelkms.resource.template.TemplateResource;
import com.richardsand.novelkms.service.AiReviewService;
import com.richardsand.novelkms.service.ArchiveService;
import com.richardsand.novelkms.service.ArtifactService;
import com.richardsand.novelkms.service.ArtifactStorage;
import com.richardsand.novelkms.service.BillingService;
import com.richardsand.novelkms.service.CodexAiService;
import com.richardsand.novelkms.service.CodexExportService;
import com.richardsand.novelkms.service.EpubExportService;
import com.richardsand.novelkms.service.ExportService;
import com.richardsand.novelkms.service.ImportService;
import com.richardsand.novelkms.service.PdfExportService;
import com.richardsand.novelkms.service.RegistrationNotificationService;
import com.richardsand.novelkms.service.HumanReviewService;
import com.richardsand.novelkms.service.ReviewPublishService;
import com.richardsand.novelkms.service.ReviewAccessService;
import com.richardsand.novelkms.service.StarterContentService;
import com.richardsand.novelkms.service.TrashService;
import com.richardsand.novelkms.service.admin.AdminBillingService;
import com.richardsand.novelkms.service.admin.AdminModerationService;
import com.richardsand.novelkms.service.admin.AdminUserDeleteService;
import com.richardsand.novelkms.service.tools.CalendarToolsService;
import com.richardsand.novelkms.service.tools.OpenMeteoWeatherProvider;
import com.richardsand.novelkms.service.tools.WeatherLookupService;

import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.Managed;
import jakarta.servlet.DispatcherType;

public class NovelKmsServer extends Application<NovelKmsConfig> {
    private static final Logger   logger     = LoggerFactory.getLogger(NovelKmsServer.class);
    private boolean               isPostgres = false;
    private final BasicDataSource ds         = new BasicDataSource();

    @Override
    public void initialize(Bootstrap<NovelKmsConfig> bootstrap) {
        bootstrap.setConfigurationSourceProvider(
                new Jinja2ConfigurationSourceProvider(bootstrap.getConfigurationSourceProvider()));

        bootstrap.addBundle(
                new AssetsBundle(
                        "/webapp",
                        "/app/",
                        "index.html",
                        "webapp-assets"));
        bootstrap.addBundle(
                new AssetsBundle(
                        "/site",
                        "/",
                        "index.html",
                        "site-assets"));
    }

    @Override
    public void run(NovelKmsConfig config, Environment env) throws Exception {
        String jdbcUrl   = config.getDatabase().url;

        logger.info("Database URL: {}", jdbcUrl);
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:")) {
            logger.info("Loading POSTGRESQL driver");
            ds.setDriverClassName("org.postgresql.Driver");
            isPostgres = true; 
        } else if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:h2:")) {
            logger.info("Loading H2 driver");
            ds.setDriverClassName("org.h2.Driver");
        } else {
        	throw new RuntimeException("Unsupported database type: " + jdbcUrl);
        }

        ds.setUrl(jdbcUrl);
        ds.setUsername(config.getDatabase().getAdminUser());
        ds.setPassword(config.getDatabase().getAdminPwd());
        ds.setMinIdle(config.getDatabase().getMinIdle());
        ds.setMaxIdle(config.getDatabase().getMaxIdle());
        ds.setMaxOpenPreparedStatements(config.getDatabase().getMaxOpenPreparedStatements());
        ds.setMaxTotal(config.getDatabase().getMaxTotal());
        ds.setMaxWait(config.getDatabase().getMaxWait());
        ds.setRemoveAbandonedTimeout(config.getDatabase().getRemoveAbandonedTimeout());
        ds.setDefaultAutoCommit(true);
        ds.setFastFailValidation(true);
        ds.setLogAbandoned(true);
        ds.setRemoveAbandonedOnBorrow(true);

        Flyway.configure()
                .dataSource(jdbcUrl, config.getDatabase().getAdminUser(), config.getDatabase().getAdminPwd())
                .locations("classpath:db/migration/" + (isPostgres ? "postgresql" : "h2"))
                .load()
                .migrate();

        // Object Mapper
        ObjectMapper mapper = env.getObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // DAOs
        AccountDao            accountDao            = new AccountDao(ds);
        AdminAuditDao         adminAuditDao         = new AdminAuditDao(ds);
        AdminMetricsDao       adminMetricsDao       = new AdminMetricsDao(ds);
        AdminUserDao          adminUserDao          = new AdminUserDao(ds);
        AiFormInstructionsDao aiFormInstructionsDao = new AiFormInstructionsDao(ds);
        AiPromptTemplateDao   aiPromptTemplateDao   = new AiPromptTemplateDao(ds);
        AiReviewDao           aiReviewDao           = new AiReviewDao(ds);
        AuthDao               authDao               = new AuthDao(ds);
        BookDao               bookDao               = new BookDao(ds);
        BookSummaryDao        bookSummaryDao        = new BookSummaryDao(ds);
        BookOutlineDao        bookOutlineDao        = new BookOutlineDao(ds);
        ChapterDao            chapterDao            = new ChapterDao(ds);
        ChapterMemoryDao      chapterMemoryDao      = new ChapterMemoryDao(ds);
        ChapterSummaryDao     chapterSummaryDao     = new ChapterSummaryDao(ds);
        ChapterEditorialDao   chapterEditorialDao   = new ChapterEditorialDao(ds);
        CodexDao              codexDao              = new CodexDao(ds);
        CodexCategoryDao      codexCategoryDao      = new CodexCategoryDao(ds);
        CodexTypeFieldDao     codexTypeFieldDao     = new CodexTypeFieldDao(ds);
        CodexTypeDao          codexTypeDao          = new CodexTypeDao(ds, codexTypeFieldDao);
        EditorSettingsDao     editorSettingsDao     = new EditorSettingsDao(ds);
        ArchiveDao            archiveDao            = new ArchiveDao(ds);
        ArtifactNodeDao       artifactNodeDao       = new ArtifactNodeDao(ds);
        ArtifactBlobDao       artifactBlobDao       = new ArtifactBlobDao(ds);
        MemoryTemplateDao     memoryTemplateDao     = new MemoryTemplateDao(ds);
        PageLayoutDao         pageLayoutDao         = new PageLayoutDao(ds);
        PartDao               partDao               = new PartDao(ds);
        ProjectDao            projectDao            = new ProjectDao(ds);
        ReviewProfileDao      reviewProfileDao      = new ReviewProfileDao(ds);
        ReviewRequestDao      reviewRequestDao      = new ReviewRequestDao(ds);
        ReviewSnapshotDao     reviewSnapshotDao     = new ReviewSnapshotDao(ds);
        ReviewQueueDao        reviewQueueDao        = new ReviewQueueDao(ds);
        ReviewMetricsDao      reviewMetricsDao      = new ReviewMetricsDao(ds);
        HumanReviewDao        humanReviewDao        = new HumanReviewDao(ds);
        UserBlockDao          userBlockDao          = new UserBlockDao(ds);
        ContentReportDao      contentReportDao      = new ContentReportDao(ds);
        SceneDao              sceneDao              = new SceneDao(ds);
        TemplateDao           templateDao           = new TemplateDao(ds);
        TenantAccessDao       tenantAccessDao       = new TenantAccessDao(ds);
        TrashDao              trashDao              = new TrashDao(ds);
        UserPreferenceDao     userPreferenceDao     = new UserPreferenceDao(ds);
        UserStyleDao          userStyleDao          = new UserStyleDao(ds);

        // Billing DAOs
        UserSubscriptionDao   userSubscriptionDao   = new UserSubscriptionDao(ds);
        StripeWebhookEventDao stripeWebhookEventDao = new StripeWebhookEventDao(ds);

        // Services
        AdminBillingService adminBillingService = new AdminBillingService(userSubscriptionDao,
                adminAuditDao,
                adminUserDao,
                authDao,
                mapper);
        AdminModerationService adminModerationService = new AdminModerationService(
                contentReportDao,
                reviewRequestDao,
                humanReviewDao,
                reviewProfileDao,
                adminAuditDao,
                mapper);
        ArchiveService      archiveService      = new ArchiveService(archiveDao);
        ArtifactStorage     artifactStorage     = new ArtifactStorage(
                config.getArtifacts() != null ? config.getArtifacts().storageDir : null);
        ArtifactService     artifactService     = new ArtifactService(ds, artifactNodeDao, artifactBlobDao,
                artifactStorage, config);
        BillingService      billingService      = new BillingService(userSubscriptionDao, config);

        AdminUserDeleteService adminUserDeleteService = new AdminUserDeleteService(
                ds,
                adminUserDao,
                adminAuditDao,
                userSubscriptionDao,
                artifactStorage,
                config,
                mapper);

        EpubExportService               epubExportService               = new EpubExportService(bookDao, partDao, chapterDao, sceneDao, projectDao);
        ExportService                   exportService                   = new ExportService(bookDao, partDao, chapterDao, sceneDao, projectDao, templateDao, pageLayoutDao);
        PdfExportService                pdfExportService                = new PdfExportService(bookDao, partDao, chapterDao, sceneDao, projectDao, templateDao, pageLayoutDao);
        ImportService                   importService                   = new ImportService(bookDao, partDao, chapterDao, sceneDao, projectDao);
        OAuthService                    oauthService                    = new OAuthService(config.getAuth(), authDao);
        RegistrationNotificationService registrationNotificationService = new RegistrationNotificationService(config.getNotifications());
        ReviewPublishService            reviewPublishService            = new ReviewPublishService(
                ds, chapterDao, sceneDao, bookDao, projectDao,
                reviewProfileDao, reviewRequestDao, reviewSnapshotDao);
        ReviewAccessService             reviewAccessService             = new ReviewAccessService(
                reviewRequestDao, reviewSnapshotDao, reviewProfileDao, reviewQueueDao, userBlockDao);
        HumanReviewService              humanReviewService              = new HumanReviewService(
                reviewRequestDao, reviewSnapshotDao, reviewProfileDao, humanReviewDao, userBlockDao);
        SessionService                  sessionService                  = new SessionService(authDao, config.getAuth());
        StarterContentService           starterContentService           = new StarterContentService(projectDao, bookDao, partDao, chapterDao, sceneDao);
        TrashService                    trashService                    = new TrashService(trashDao, projectDao, bookDao, chapterDao, sceneDao,
                artifactNodeDao, artifactStorage);

        // AI Credential DAO
        SecretCipher    secretCipher    = new SecretCipher(
                config.getSecurity() != null ? config.getSecurity().encryptionKey : null);
        AiCredentialDao aiCredentialDao = new AiCredentialDao(ds, secretCipher);

        // AI Review Service — provider registry
        OpenAiProvider     openAiProvider     = new OpenAiProvider();
        AnthropicProvider  anthropicProvider  = new AnthropicProvider();
        GeminiProvider     geminiProvider     = new GeminiProvider();
        AiProviderRegistry aiProviderRegistry = AiProviderRegistry.create(openAiProvider, anthropicProvider, geminiProvider);

        AiReviewService aiReviewService = new AiReviewService(
                chapterDao, sceneDao, bookDao, aiCredentialDao, aiReviewDao,
                aiFormInstructionsDao, chapterMemoryDao, memoryTemplateDao,
                aiPromptTemplateDao,
                chapterSummaryDao, bookSummaryDao,
                chapterEditorialDao,
                codexDao, codexCategoryDao, aiProviderRegistry.getAiProviderMap());

        // Codex entry DOCX export/import and AI fill services
        CodexExportService codexExportService = new CodexExportService(
                sceneDao, chapterDao, codexCategoryDao);
        CodexAiService     codexAiService     = new CodexAiService(
                sceneDao, chapterDao, bookDao, codexDao, codexCategoryDao,
                aiCredentialDao, chapterSummaryDao, aiProviderRegistry.getAiProviderMap());

        // Author utility tools
        CalendarToolsService calendarToolsService = new CalendarToolsService();
        WeatherLookupService weatherLookupService = new WeatherLookupService(
                new OpenMeteoWeatherProvider(), aiCredentialDao, aiProviderRegistry.getAiProviderMap());

        // OOTB resources
        env.jersey().register(RolesAllowedDynamicFeature.class);

        // Custom resources
        env.jersey().register(AccountResource.class);
        env.jersey().register(AdminAuditResource.class);
        env.jersey().register(AdminBillingResource.class);
        env.jersey().register(AdminMetricsResource.class);
        env.jersey().register(AdminModerationResource.class);
        env.jersey().register(AdminUserResource.class);
        env.jersey().register(AdminSystemResource.class);
        env.jersey().register(AiFormInstructionsResource.class);
        env.jersey().register(AiContextResource.class);
        env.jersey().register(AiReviewResource.class);
        env.jersey().register(AiCredentialResource.class);
        env.jersey().register(ArchiveResource.class);
        env.jersey().register(ArtifactResource.class);
        env.jersey().register(AuthenticationFilter.class);
        env.jersey().register(AuthResource.class);
        env.jersey().register(BillingResource.class);
        env.jersey().register(BookResource.class);
        env.jersey().register(BookSummaryTemplateResource.class);
        env.jersey().register(ChapterMemoryResource.class);
        env.jersey().register(ChapterSummaryTemplateResource.class);
        env.jersey().register(CodexEntryResource.class);
        env.jersey().register(EditorialResource.class);
        env.jersey().register(EditorialTemplateResource.class);
        env.jersey().register(BookOutlineResource.class);
        env.jersey().register(ChapterResource.class);
        env.jersey().register(CodexResource.class);
        env.jersey().register(EditorSettingsResource.class);
        env.jersey().register(ExportResource.class);
        env.jersey().register(ImportResource.class);
        env.jersey().register(MemoryTemplateResource.class);
        env.jersey().register(MultiPartFeature.class);
        env.jersey().register(PageLayoutResource.class);
        env.jersey().register(PartResource.class);
        env.jersey().register(ProjectResource.class);
        env.jersey().register(HumanReviewResource.class);
        env.jersey().register(ReviewProfileResource.class);
        env.jersey().register(ReviewPublishResource.class);
        env.jersey().register(ReviewRequestResource.class);
        env.jersey().register(ReviewQueueResource.class);
        env.jersey().register(ReviewSafetyResource.class);
        env.jersey().register(SceneResource.class);
        env.jersey().register(StripeWebhookResource.class);
        env.jersey().register(StyleResource.class);
        env.jersey().register(SubscriptionAuthorizationFilter.class);
        env.jersey().register(SummaryResource.class);
        env.jersey().register(TemplateResource.class);
        env.jersey().register(TenantAuthorizationFilter.class);
        env.jersey().register(TrashResource.class);
        env.jersey().register(ToolsResource.class);
        env.jersey().register(UserPreferenceResource.class);

        // SPA fallback
        env.servlets()
                .addFilter("spa-fallback", new SpaFallbackFilter())
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");

        // Jersey Registrations
        env.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(accountDao).to(AccountDao.class);
                bind(adminAuditDao).to(AdminAuditDao.class);
                bind(adminBillingService).to(AdminBillingService.class);
                bind(adminMetricsDao).to(AdminMetricsDao.class);
                bind(adminUserDao).to(AdminUserDao.class);
                bind(adminUserDeleteService).to(AdminUserDeleteService.class);
                bind(aiCredentialDao).to(AiCredentialDao.class);
                bind(aiFormInstructionsDao).to(AiFormInstructionsDao.class);
                bind(aiPromptTemplateDao).to(AiPromptTemplateDao.class);
                bind(aiProviderRegistry).to(AiProviderRegistry.class);
                bind(aiReviewDao).to(AiReviewDao.class);
                bind(aiReviewService).to(AiReviewService.class);
                bind(archiveDao).to(ArchiveDao.class);
                bind(archiveService).to(ArchiveService.class);
                bind(artifactNodeDao).to(ArtifactNodeDao.class);
                bind(artifactBlobDao).to(ArtifactBlobDao.class);
                bind(artifactStorage).to(ArtifactStorage.class);
                bind(artifactService).to(ArtifactService.class);
                bind(authDao).to(AuthDao.class);
                bind(billingService).to(BillingService.class);
                bind(bookDao).to(BookDao.class);
                bind(bookSummaryDao).to(BookSummaryDao.class);
                bind(bookOutlineDao).to(BookOutlineDao.class);
                bind(chapterDao).to(ChapterDao.class);
                bind(chapterMemoryDao).to(ChapterMemoryDao.class);
                bind(chapterSummaryDao).to(ChapterSummaryDao.class);
                bind(chapterEditorialDao).to(ChapterEditorialDao.class);
                bind(codexAiService).to(CodexAiService.class);
                bind(codexCategoryDao).to(CodexCategoryDao.class);
                bind(codexDao).to(CodexDao.class);
                bind(codexTypeFieldDao).to(CodexTypeFieldDao.class);
                bind(codexTypeDao).to(CodexTypeDao.class);
                bind(codexExportService).to(CodexExportService.class);
                bind(config).to(NovelKmsConfig.class);
                bind(editorSettingsDao).to(EditorSettingsDao.class);
                bind(epubExportService).to(EpubExportService.class);
                bind(exportService).to(ExportService.class);
                bind(importService).to(ImportService.class);
                bind(mapper).to(ObjectMapper.class);
                bind(memoryTemplateDao).to(MemoryTemplateDao.class);
                bind(oauthService).to(OAuthService.class);
                bind(pageLayoutDao).to(PageLayoutDao.class);
                bind(partDao).to(PartDao.class);
                bind(pdfExportService).to(PdfExportService.class);
                bind(projectDao).to(ProjectDao.class);
                bind(reviewProfileDao).to(ReviewProfileDao.class);
                bind(reviewPublishService).to(ReviewPublishService.class);
                bind(reviewAccessService).to(ReviewAccessService.class);
                bind(humanReviewService).to(HumanReviewService.class);
                bind(reviewRequestDao).to(ReviewRequestDao.class);
                bind(reviewSnapshotDao).to(ReviewSnapshotDao.class);
                bind(reviewQueueDao).to(ReviewQueueDao.class);
                bind(reviewMetricsDao).to(ReviewMetricsDao.class);
                bind(humanReviewDao).to(HumanReviewDao.class);
                bind(userBlockDao).to(UserBlockDao.class);
                bind(contentReportDao).to(ContentReportDao.class);
                bind(adminModerationService).to(AdminModerationService.class);
                bind(registrationNotificationService).to(RegistrationNotificationService.class);
                bind(sceneDao).to(SceneDao.class);
                bind(starterContentService).to(StarterContentService.class);
                bind(sessionService).to(SessionService.class);
                bind(stripeWebhookEventDao).to(StripeWebhookEventDao.class);
                bind(templateDao).to(TemplateDao.class);
                bind(tenantAccessDao).to(TenantAccessDao.class);
                bind(trashDao).to(TrashDao.class);
                bind(trashService).to(TrashService.class);
                bind(calendarToolsService).to(CalendarToolsService.class);
                bind(weatherLookupService).to(WeatherLookupService.class);
                bind(userStyleDao).to(UserStyleDao.class);
                bind(userSubscriptionDao).to(UserSubscriptionDao.class);
                bind(userPreferenceDao).to(UserPreferenceDao.class);
            }
        });

        // Lifecycle and health checks
        env.lifecycle().manage(new Managed() {
            @Override
            public void start() {
            }

            @Override
            public void stop() throws Exception {
                oauthService.close();
            }
        });
        env.lifecycle().manage(new StartStopNotifier(config));
        env.healthChecks().register("database", new DataSourceHealthCheck(ds));
     }

    public static void main(String[] args) throws Exception {
        new NovelKmsServer().run(args);
    }
}
