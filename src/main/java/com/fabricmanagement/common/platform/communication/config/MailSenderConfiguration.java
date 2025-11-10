package com.fabricmanagement.common.platform.communication.config;

import com.fabricmanagement.common.platform.communication.infrastructure.NoOpJavaMailSender;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailSenderConfiguration {

    private final MailProperties mailProperties;

    public MailSenderConfiguration(ObjectProvider<MailProperties> mailPropertiesProvider) {
        this.mailProperties = mailPropertiesProvider.getIfAvailable(MailProperties::new);
    }

    @Bean
    @Primary
    public JavaMailSender javaMailSender(Environment environment) {
        if (StringUtils.hasText(mailProperties.getHost())) {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(mailProperties.getHost());
            sender.setPort(mailProperties.getPort());
            sender.setProtocol(mailProperties.getProtocol());
            sender.setUsername(mailProperties.getUsername());
            sender.setPassword(mailProperties.getPassword());

            Properties javaMailProperties = sender.getJavaMailProperties();
            javaMailProperties.putAll(mailProperties.getProperties());

            if (StringUtils.hasText(environment.getProperty("spring.mail.default-encoding"))) {
                sender.setDefaultEncoding(environment.getProperty("spring.mail.default-encoding"));
            }

            return sender;
        }
        return new NoOpJavaMailSender();
    }
}

