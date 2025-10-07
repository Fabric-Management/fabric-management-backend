package com.fabricmanagement.user.application.service;

import com.fabricmanagement.shared.domain.exception.AccountLockedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Login Attempt Tracking Service
 * 
 * Tracks failed login attempts and locks accounts after too many failures.
 * Uses Redis for distributed tracking across multiple instances.
 * 
 * Configuration properties:
 * - security.login-attempt.max-attempts: Maximum failed attempts before lockout
 * - security.login-attempt.lockout-duration-minutes: Lockout duration
 */
@Service
@Slf4j
public class LoginAttemptService {

    private final RedisTemplate<String, String> redisTemplate;
    private final com.fabricmanagement.user.infrastructure.audit.SecurityAuditLogger auditLogger;

    // Configurable values from application.yml
    @Value("${security.login-attempt.max-attempts:5}")
    private int maxAttempts;
    
    @Value("${security.login-attempt.lockout-duration-minutes:15}")
    private int lockoutDurationMinutes;

    // Redis key prefixes (convention-based, can stay as constants)
    private static final String ATTEMPTS_KEY_PREFIX = "login:attempts:";
    private static final String LOCKOUT_KEY_PREFIX = "login:lockout:";

    public LoginAttemptService(RedisTemplate<String, String> redisTemplate, 
                               com.fabricmanagement.user.infrastructure.audit.SecurityAuditLogger auditLogger) {
        this.redisTemplate = redisTemplate;
        this.auditLogger = auditLogger;
    }

    /**
     * Records a failed login attempt
     * 
     * @param contactValue Email or phone that attempted login
     */
    public void recordFailedAttempt(String contactValue) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + contactValue;
        
        // Increment attempt counter
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        
        if (attempts == null) {
            attempts = 1L;
        }

        // Set expiry on first attempt
        if (attempts == 1) {
            redisTemplate.expire(attemptsKey, lockoutDurationMinutes, TimeUnit.MINUTES);
        }

        log.warn("Failed login attempt {} for contact: {}", attempts, contactValue);

        // Lock account if max attempts reached
        if (attempts >= maxAttempts) {
            lockAccount(contactValue);
            auditLogger.logAccountLockout(contactValue, maxAttempts, lockoutDurationMinutes);
            log.warn("Account locked due to {} failed attempts: {}", maxAttempts, contactValue);
        }
    }

    /**
     * Clears failed attempts on successful login
     * 
     * @param contactValue Email or phone that logged in successfully
     */
    public void clearFailedAttempts(String contactValue) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + contactValue;
        String lockoutKey = LOCKOUT_KEY_PREFIX + contactValue;
        
        redisTemplate.delete(attemptsKey);
        redisTemplate.delete(lockoutKey);
        
        log.debug("Cleared failed attempts for contact: {}", contactValue);
    }

    /**
     * Checks if account is locked
     * 
     * @param contactValue Email or phone to check
     * @throws AccountLockedException if account is locked
     */
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

    /**
     * Gets current failed attempt count
     * 
     * @param contactValue Email or phone to check
     * @return Number of failed attempts
     */
    public int getFailedAttempts(String contactValue) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + contactValue;
        String value = redisTemplate.opsForValue().get(attemptsKey);
        
        return value != null ? Integer.parseInt(value) : 0;
    }

    /**
     * Locks an account for the configured duration
     * 
     * @param contactValue Email or phone to lock
     */
    private void lockAccount(String contactValue) {
        String lockoutKey = LOCKOUT_KEY_PREFIX + contactValue;
        
        // Set lockout flag with expiry
        redisTemplate.opsForValue().set(
            lockoutKey, 
            String.valueOf(System.currentTimeMillis()),
            lockoutDurationMinutes,
            TimeUnit.MINUTES
        );
        
        log.warn("Account locked for {} minutes: {}", lockoutDurationMinutes, contactValue);
    }

    /**
     * Manually unlocks an account (for admin use)
     * 
     * @param contactValue Email or phone to unlock
     */
    public void unlockAccount(String contactValue) {
        clearFailedAttempts(contactValue);
        log.info("Account manually unlocked: {}", contactValue);
    }

    /**
     * Gets remaining lockout time in minutes
     * 
     * @param contactValue Email or phone to check
     * @return Remaining minutes, or 0 if not locked
     */
    public int getRemainingLockoutMinutes(String contactValue) {
        String lockoutKey = LOCKOUT_KEY_PREFIX + contactValue;
        Long ttl = redisTemplate.getExpire(lockoutKey, TimeUnit.MINUTES);
        
        return ttl != null && ttl > 0 ? ttl.intValue() : 0;
    }
}
