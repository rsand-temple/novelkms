package com.richardsand.novelkms.dropwizard.health;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.service.StartStopNotificationService;

import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;

public class StartStopNotifier implements Managed {
    Logger logger = LoggerFactory.getLogger(getClass());
    
    @Inject
    NovelKmsConfig config;

    StartStopNotificationService sns = new StartStopNotificationService(config.getNotifications());

    @Override
    public void start() throws Exception {
        var props = logBuildInfo();
        sns.notifyStartup(props);
    }

    @Override
    public void stop() throws Exception {
        sns.notifyStop();
    }

    private Properties logBuildInfo() {
        var props = new Properties();
        try (var in = getClass().getClassLoader().getResourceAsStream("build.properties")) {
            if (in != null) {
                props.load(in);
                logger.info("NovelKMS Version {} Build {}",
                        props.getProperty("app.version", "unknown"),
                        props.getProperty("build.number", "unknown"));
            }
        } catch (Exception e) {
            logger.warn("Could not read build.properties", e);
        }
        return props;
    }

}
