package com.richardsand.novelkms.service;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.NovelKmsConfig;

public class StartStopNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(StartStopNotificationService.class);

    private final NovelKmsConfig.Notifications.Registration config;

    public StartStopNotificationService(NovelKmsConfig.Notifications notifications) {
        this.config = notifications == null ? null : notifications.registration;
    }

    public void notifyStartup(Properties props) {
        if (!enabled()) {
            return;
        }

        try {
            send(props);
        } catch (Exception e) {
            logger.warn("Could not send startup notification", e);
        }
    }

    public void notifyStop() {
        if (!enabled()) {
            return;
        }

        try {
            sendStop();
        } catch (Exception e) {
            logger.warn("Could not send startup notification", e);
        }
    }

    private boolean enabled() {
        if (config == null || !config.enabled) {
            return false;
        }

        if (StringUtils.isBlank(config.smtpHost)) {
            logger.warn("Registration notification is enabled but smtpHost is blank");
            return false;
        }

        if (StringUtils.isBlank(config.supportAddress)) {
            logger.warn("Registration notification is enabled but supportAddress is blank");
            return false;
        }

        return true;
    }

    private void send(Properties versionProps) throws Exception {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", config.smtpHost);
        props.put("mail.smtp.port", Integer.toString(config.smtpPort));
        props.put("mail.smtp.auth", Boolean.toString(StringUtils.isNotBlank(config.smtpUsername)));
        props.put("mail.smtp.starttls.enable", Boolean.toString(config.startTls));
        props.put("mail.smtp.ssl.enable", Boolean.toString(config.ssl));

        Session session = Session.getInstance(props, authenticator());

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.supportAddress, false));
        message.setSubject(subject(), StandardCharsets.UTF_8.name());
        message.setText(body(versionProps), StandardCharsets.UTF_8.name());

        Transport.send(message);
    }

    private void sendStop() throws Exception {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", config.smtpHost);
        props.put("mail.smtp.port", Integer.toString(config.smtpPort));
        props.put("mail.smtp.auth", Boolean.toString(StringUtils.isNotBlank(config.smtpUsername)));
        props.put("mail.smtp.starttls.enable", Boolean.toString(config.startTls));
        props.put("mail.smtp.ssl.enable", Boolean.toString(config.ssl));

        Session session = Session.getInstance(props, authenticator());

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.supportAddress, false));
        message.setSubject(subjectStop(), StandardCharsets.UTF_8.name());
        message.setText(bodyStop());

        Transport.send(message);
    }

    private Authenticator authenticator() {
        if (StringUtils.isBlank(config.smtpUsername)) {
            return null;
        }

        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.smtpUsername, nullToEmpty(config.smtpPassword));
            }
        };
    }

    private String fromAddress() {
        if (StringUtils.isNotBlank(config.fromAddress)) {
            return config.fromAddress;
        }
        if (StringUtils.isNotBlank(config.smtpUsername)) {
            return config.smtpUsername;
        }
        return config.supportAddress;
    }

    private String subject() {
        String prefix = StringUtils.isBlank(config.subjectPrefix) ? "[NovelKMS]" : config.subjectPrefix.trim();
        return prefix + " Service startup";
    }
    
    private String subjectStop() {
        String prefix = StringUtils.isBlank(config.subjectPrefix) ? "[NovelKMS]" : config.subjectPrefix.trim();
        return prefix + " Service shutdown";
    }


    private String body(Properties props) {
        return """
                NovelKMS startup completed at %s

                App version: %s
                Build number: %s
                """
                .formatted(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(ZonedDateTime.now()),
                        props.getProperty("app.version", "unknown"),
                        props.getProperty("build.number", "unknown"));
    }

    private String bodyStop() {
        return """
                NovelKMS shutdown at %s
                """
                .formatted(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(ZonedDateTime.now()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}