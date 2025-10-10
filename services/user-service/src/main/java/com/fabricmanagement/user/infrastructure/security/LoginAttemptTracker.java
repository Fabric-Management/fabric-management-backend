package com.fabricmanagement.user.infrastructure.security;

import com.fabricmanagement.shared.domain.exception.AccountLockedException;
import com.fabricmanagement.user.infrastructure.audit.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptTracker {

    private final RedisTemplate<String, String> redisTemplate;
    private final SecurityAuditLogger auditLogger;

    @Value("${security.login-attempt.max-attempts:5}")
    private int maxAttempts;
    
    @Value("${security.login-attempt.lockout-duration-minutes:15}")
    private int lockoutDurationMinutes;

    private static final String ATTEMPTS_KEY_PREFIX = "login:attempts:";
    private static final String LOCKOUT_KEY_PREFIX = "login:lockout:";

    public void recordFailedAttempt(String contactValue) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + contactValue;
        
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        
        if (attempts == null) {
            attempts = 1L;
        }

        if (attempts == 1) {
            redisTemplate.expire(attemptsKey, lockoutDurationMinutes, TimeUnit.MINUTES);
        }

        log.warn("Failed login attempt {} for contact: {}", attempts, contactValue);

        if (attempts >= maxAttempts) {
            lockAccount(contactValue);
            auditLogger.logAccountLockout(contactValue, maxAttempts, lockoutDurationMinutes);
            log.warn("Account locked due to {} failed attempts: {}", maxAttempts, contactValue);
        }
    }

    public void clearFailedAttempts(String contactValue) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + contactValue;
        String lockoutKey = LOCKOUT_KEY_PREFIX + contactValue;
        
        redisTemplate.delete(attemptsKey);
        redisTemplate.delete(lockoutKey);
        
        log.debug("Cleared failed attempts for contact: {}", contactValue);
    }

    public void checkIfLocked(String contactValue) {
        String lockoutKey = LOCKOUT_KEY_PREFIX + contactValue;
        
        String lockoutValue = redisTemplate.opsForValue().get(lockoutKey);
        
        if (lockoutValue != null) {
            Long ttl = redisTemplate.getExpire(lockoutKey, TimeUnit.MINUTES);
            int remainingMinutes = ttl != null ? ttl.intValue() : lockoutDurationMinutes;
            
            log.warn("Login attempt blocked - account locked: {}", contactValue);
            throw new AccountLockedException(contactValue, remainingMinutes);
        }
    }

    public int getFailedAttempts(String contactValue) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + contactValue;
        String value = redisTemplate.opsForValue().get(attemptsKey);
        
        return value != null ? Integer.parseInt(value) : 0;
    }

    public void unlockAccount(String contactValue) {
        clearFailedAttempts(contactValue);
        log.info("Account manually unlocked: {}", contactValue);
    }

    public int getRemainingLockoutMinutes(String contactValue) {
        String lockoutKey = LOCKOUT_KEY_PREFIX + contactValue;
        Long ttl = redisTemplate.getExpire(lockoutKey, TimeUnit.MINUTES);
        
        return ttl != null && ttl > 0 ? ttl.intValue() : 0;
    }

    private void lockAccount(String contactValue) {
        String lockoutKey = LOCKOUT_KEY_PREFIX + contactValue;
        
        redisTemplate.opsForValue().set(
            lockoutKey, 
            String.valueOf(System.currentTimeMillis()),
            lockoutDurationMinutes,
            TimeUnit.MINUTES
        );
        
        log.warn("Account locked for {} minutes: {}", lockoutDurationMinutes, contactValue);
    }
}

