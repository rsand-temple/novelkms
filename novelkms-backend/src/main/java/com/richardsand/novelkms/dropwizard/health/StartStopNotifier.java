package com.richardsand.novelkms.dropwizard.health;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.service.StartStopNotificationService;

import io.dropwizard.lifecycle.Managed;

public class StartStopNotifier implements Managed {
    Logger logger = LoggerFactory.getLogger(getClass());
    
    final NovelKmsConfig config;
    
    public StartStopNotifier(NovelKmsConfig config) {
        this.config = config;
    }

    StartStopNotificationService sns = null;

    @Override
    public void start() throws Exception {
        var props = logBuildInfo();
        sns = new StartStopNotificationService(config.getNotifications());
        if (sns != null)
            sns.notifyStartup(props);
    }

    @Override
    public void stop() throws Exception {
        logger.info("Shutting down...");
        if (sns != null)
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
