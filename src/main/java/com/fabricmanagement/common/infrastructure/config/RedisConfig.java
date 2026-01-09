package com.fabricmanagement.common.infrastructure.config;

import com.fabricmanagement.human.compliance.localization.domain.HrLocalizationCacheNames;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableConfigurationProperties(RedisProperties.class)
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = false)
@Slf4j
public class RedisConfig {

  @Bean
  public LettuceConnectionFactory redisConnectionFactory(RedisProperties redisProperties) {
    RedisStandaloneConfiguration config =
        new RedisStandaloneConfiguration(redisProperties.getHost(), redisProperties.getPort());

    // Only set password if it's provided and not blank
    String password = redisProperties.getPassword();
    boolean hasPassword = password != null && !password.isBlank() && !password.isEmpty();

    if (hasPassword) {
      config.setPassword(RedisPassword.of(password));
      log.info(
          "✅ Redis connection configured: host={}, port={}, db={}, password=*** (length={})",
          redisProperties.getHost(),
          redisProperties.getPort(),
          config.getDatabase(),
          password != null ? password.length() : 0);
    } else {
      log.warn(
          "⚠️ Redis connection configured WITHOUT password: host={}, port={}, db={}. "
              + "If Redis requires password, connection will fail. Check REDIS_PASSWORD environment variable. "
              + "Current password value: '{}'",
          redisProperties.getHost(),
          redisProperties.getPort(),
          config.getDatabase(),
          password != null ? "(empty)" : "(null)");
    }

    config.setDatabase(redisProperties.getDatabase());

    LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
    // ✅ Lazy connection: Don't validate on startup, validate on first use
    // This prevents startup failure if Redis is temporarily unavailable
    factory.setValidateConnection(false);

    return factory;
  }

  @Bean
  public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
    return template;
  }

  @Bean
  public CacheManager redisCacheManager(
      LettuceConnectionFactory connectionFactory,
      @Value("${REDIS_CACHE_DEFAULT_TTL:PT5M}") Duration defaultTtl,
      @Value("${HR_POLICY_PACK_CACHE_TTL:PT15M}") Duration policyPackTtl) {
    RedisCacheConfiguration defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(defaultTtl)
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()));

    Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
    cacheConfigurations.put(
        HrLocalizationCacheNames.ACTIVE_POLICY_PACK, defaultConfig.entryTtl(policyPackTtl));

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(cacheConfigurations)
        .build();
  }
}
