package com.fabricmanagement.shared.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Configuration
public class MessageSourceConfig {
    
    @Value("${MESSAGE_CACHE_SECONDS:3600}")
    private int messageCacheSeconds;
    
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        
        source.setBasenames(
            "classpath:messages/auth_messages",
            "classpath:messages/user_messages",
            "classpath:messages/company_messages",
            "classpath:messages/contact_messages",
            "classpath:messages/error_messages"
        );
        
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(Locale.ENGLISH);
        source.setCacheSeconds(messageCacheSeconds);
        
        return source;
    }
    
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.of("tr", "TR")));
        return resolver;
    }
}

