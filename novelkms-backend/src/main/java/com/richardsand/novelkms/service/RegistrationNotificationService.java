package com.richardsand.novelkms.service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.dao.AuthDao.PendingRegistration;
import com.richardsand.novelkms.model.AppUser;

public class RegistrationNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(RegistrationNotificationService.class);

    private final NovelKmsConfig.Notifications.Registration config;

    public RegistrationNotificationService(NovelKmsConfig.Notifications notifications) {
        this.config = notifications == null ? null : notifications.registration;
    }

    public void notifyRegistration(AppUser user, PendingRegistration pending, String remoteAddr, String userAgent) {
        if (!enabled()) {
            return;
        }

        try {
            send(user, pending, remoteAddr, userAgent);
        } catch (Exception e) {
            logger.warn("Could not send registration notification for userId={}, email={}",
                    user == null ? null : user.id(),
                    user == null ? null : user.emailAddress(),
                    e);
        }
    }

    private boolean enabled() {
        if (config == null || !config.enabled) {
            return false;
        }

        if (isBlank(config.smtpHost)) {
            logger.warn("Registration notification is enabled but smtpHost is blank");
            return false;
        }

        if (isBlank(config.supportAddress)) {
            logger.warn("Registration notification is enabled but supportAddress is blank");
            return false;
        }

        return true;
    }

    private void send(AppUser user, PendingRegistration pending, String remoteAddr, String userAgent) throws Exception {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", config.smtpHost);
        props.put("mail.smtp.port", Integer.toString(config.smtpPort));
        props.put("mail.smtp.auth", Boolean.toString(!isBlank(config.smtpUsername)));
        props.put("mail.smtp.starttls.enable", Boolean.toString(config.startTls));
        props.put("mail.smtp.ssl.enable", Boolean.toString(config.ssl));

        Session session = Session.getInstance(props, authenticator());

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.supportAddress, false));
        message.setSubject(subject(user), StandardCharsets.UTF_8.name());
        message.setText(body(user, pending, remoteAddr, userAgent), StandardCharsets.UTF_8.name());

        Transport.send(message);
    }

    private Authenticator authenticator() {
        if (isBlank(config.smtpUsername)) {
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
        if (!isBlank(config.fromAddress)) {
            return config.fromAddress;
        }
        if (!isBlank(config.smtpUsername)) {
            return config.smtpUsername;
        }
        return config.supportAddress;
    }

    private String subject(AppUser user) {
        String prefix = isBlank(config.subjectPrefix) ? "[NovelKMS]" : config.subjectPrefix.trim();
        String name   = user == null || isBlank(user.displayName()) ? "new user" : user.displayName().trim();
        return prefix + " New registration: " + name;
    }

    private String body(AppUser user, PendingRegistration pending, String remoteAddr, String userAgent) {
        var profile = pending == null ? null : pending.profile();

        return """
                New NovelKMS registration completed.

                User ID: %s
                Email: %s
                Normalized email: %s
                Email verified: %s
                Display name: %s
                First name: %s
                Last name: %s
                Mobile number: %s
                Status: %s
                Created at: %s

                OAuth provider: %s
                OAuth subject: %s
                Provider email: %s
                Provider email verified: %s

                Remote address: %s
                User-Agent: %s
                """.formatted(
                value(user == null ? null : user.id()),
                value(user == null ? null : user.emailAddress()),
                value(user == null ? null : user.normalizedEmail()),
                value(user == null ? null : user.emailVerified()),
                value(user == null ? null : user.displayName()),
                value(user == null ? null : user.firstName()),
                value(user == null ? null : user.lastName()),
                value(user == null ? null : user.mobileNumber()),
                value(user == null ? null : user.status()),
                value(user == null || user.createdAt() == null
                        ? null
                        : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(user.createdAt().atOffset(ZoneOffset.UTC))),
                value(profile == null ? null : profile.provider()),
                value(profile == null ? null : profile.subject()),
                value(profile == null ? null : profile.email()),
                value(profile == null ? null : profile.emailVerified()),
                value(remoteAddr),
                value(userAgent));
    }

    private static String value(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        return s == null ? "" : s;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}