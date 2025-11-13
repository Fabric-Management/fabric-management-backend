package com.fabricmanagement.common.platform.communication.infrastructure;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.util.Arrays;
import java.util.Objects;

public class NoOpJavaMailSender extends JavaMailSenderImpl {

    private static final Logger log = LoggerFactory.getLogger(NoOpJavaMailSender.class);

    @Override
    public void send(@NonNull SimpleMailMessage simpleMessage) throws MailException {
        emitSimple(simpleMessage);
    }

    @Override
    public void send(@NonNull SimpleMailMessage... simpleMessages) throws MailException {
        Arrays.stream(simpleMessages)
            .filter(Objects::nonNull)
            .forEach(this::emitSimple);
    }

    @Override
    public void send(@NonNull MimeMessage mimeMessage) throws MailException {
        emitMime();
    }

    @Override
    public void send(@NonNull MimeMessage... mimeMessages) throws MailException {
        emitMime();
    }

    @Override
    public void send(@NonNull MimeMessagePreparator mimeMessagePreparator) throws MailException {
        emitMime();
    }

    @Override
    public void send(@NonNull MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        emitMime();
    }

    private void emitSimple(SimpleMailMessage message) {
        log.info(
            "Mail suppressed (noop sender): to={}, subject={}",
            Arrays.toString(message.getTo()),
            message.getSubject()
        );
    }

    private void emitMime() {
        log.info("Mail suppressed (noop sender): mime message");
    }
}

