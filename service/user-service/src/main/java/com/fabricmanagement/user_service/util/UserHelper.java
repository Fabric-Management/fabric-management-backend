package com.fabricmanagement.user_service.util;

import com.fabricmanagement.user_service.entity.User;
import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.entity.enums.UserStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

public final class UserHelper {

    private static final DateTimeFormatter GREETING_TIME_FORMAT = DateTimeFormatter.ofPattern("HH");
    private static final Locale TR_LOCALE = new Locale("tr", "TR");

    private UserHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Kullanıcı için selamlama mesajı oluştur
     */
    public static String getGreeting(User user) {
        String timeBasedGreeting = getTimeBasedGreeting();
        String name = user.getFirstName() != null ? user.getFirstName() : user.getUsername();
        return String.format("%s %s! 👋", timeBasedGreeting, StringUtils.capitalize(name));
    }

    /**
     * Saate göre selamlama
     */
    public static String getTimeBasedGreeting() {
        int hour = LocalDateTime.now().getHour();

        if (hour >= 6 && hour < 11) {
            return "Günaydın";
        } else if (hour >= 11 && hour < 14) {
            return "İyi öğlenler";
        } else if (hour >= 14 && hour < 18) {
            return "İyi günler";
        } else if (hour >= 18 && hour < 22) {
            return "İyi akşamlar";
        } else {
            return "İyi geceler";
        }
    }

    /**
     * Kullanıcının görünen adını al
     */
    public static String getDisplayName(User user) {
        if (user == null) return "Misafir";

        if (StringUtils.isNotBlank(user.getFirstName()) && StringUtils.isNotBlank(user.getLastName())) {
            return user.getFirstName() + " " + user.getLastName();
        } else if (StringUtils.isNotBlank(user.getFirstName())) {
            return user.getFirstName();
        } else if (StringUtils.isNotBlank(user.getLastName())) {
            return user.getLastName();
        } else {
            return user.getUsername();
        }
    }

    /**
     * Kullanıcının kısa adını al (Avatar için)
     */
    public static String getInitials(User user) {
        if (user == null) return "?";

        String firstName = StringUtils.trimToEmpty(user.getFirstName());
        String lastName = StringUtils.trimToEmpty(user.getLastName());

        if (!firstName.isEmpty() && !lastName.isEmpty()) {
            return (firstName.charAt(0) + "" + lastName.charAt(0)).toUpperCase();
        } else if (!firstName.isEmpty()) {
            return firstName.substring(0, Math.min(2, firstName.length())).toUpperCase();
        } else if (!lastName.isEmpty()) {
            return lastName.substring(0, Math.min(2, lastName.length())).toUpperCase();
        } else {
            return user.getUsername().substring(0, Math.min(2, user.getUsername().length())).toUpperCase();
        }
    }

    /**
     * Kullanıcının erişim durumunu kontrol et
     */
    public static boolean canLogin(User user) {
        if (user == null || user.isDeleted()) {
            return false;
        }

        // Locked kontrolü
        if (user.getStatus() == UserStatus.LOCKED) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                return false;
            }
        }

        // Status kontrolü
        return user.getStatus() == UserStatus.ACTIVE ||
                user.getStatus() == UserStatus.PENDING_VERIFICATION;
    }

    /**
     * Kullanıcının belirli bir role sahip olup olmadığını kontrol et
     */
    public static boolean hasAnyRole(User user, Role... roles) {
        if (user == null || roles == null || roles.length == 0) {
            return false;
        }

        Set<Role> userRoles = user.getRoles();
        for (Role role : roles) {
            if (userRoles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kullanıcının tüm rollere sahip olup olmadığını kontrol et
     */
    public static boolean hasAllRoles(User user, Role... roles) {
        if (user == null || roles == null || roles.length == 0) {
            return false;
        }

        Set<Role> userRoles = user.getRoles();
        for (Role role : roles) {
            if (!userRoles.contains(role)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Admin veya manager kontrolü
     */
    public static boolean isAdminOrManager(User user) {
        return hasAnyRole(user, Role.ADMIN, Role.MANAGER, Role.COMPANY_MANAGER);
    }

    /**
     * Şifre değişimi gerekli mi?
     */
    public static boolean shouldChangePassword(User user, int passwordAgeDays) {
        if (user == null || !user.isHasPassword()) {
            return true;
        }

        if (user.getPasswordChangedAt() == null) {
            return true;
        }

        return user.getPasswordChangedAt().plusDays(passwordAgeDays).isBefore(LocalDateTime.now());
    }

    /**
     * Son giriş bilgisi metni
     */
    public static String getLastLoginText(User user) {
        if (user == null || user.getLastLoginAt() == null) {
            return "Henüz giriş yapmadınız";
        }

        LocalDateTime lastLogin = user.getLastLoginAt();
        LocalDateTime now = LocalDateTime.now();

        long minutesAgo = java.time.Duration.between(lastLogin, now).toMinutes();

        if (minutesAgo < 1) {
            return "Az önce";
        } else if (minutesAgo < 60) {
            return minutesAgo + " dakika önce";
        } else if (minutesAgo < 1440) { // 24 saat
            long hoursAgo = minutesAgo / 60;
            return hoursAgo + " saat önce";
        } else if (minutesAgo < 10080) { // 7 gün
            long daysAgo = minutesAgo / 1440;
            return daysAgo + " gün önce";
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", TR_LOCALE);
            return lastLogin.format(formatter);
        }
    }

    /**
     * Account durumu mesajı
     */
    public static String getAccountStatusMessage(User user) {
        if (user == null) {
            return "Kullanıcı bulunamadı";
        }

        switch (user.getStatus()) {
            case ACTIVE:
                return user.isEmailVerified() ? "Hesabınız aktif" : "Email doğrulaması bekleniyor";
            case INACTIVE:
                return "Hesabınız pasif durumda";
            case SUSPENDED:
                return "Hesabınız askıya alınmış";
            case PENDING_VERIFICATION:
                return "Hesap doğrulaması bekleniyor";
            case LOCKED:
                if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                    long minutesLeft = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes();
                    return String.format("Hesabınız %d dakika kilitli", minutesLeft);
                }
                return "Hesabınız kilitli";
            default:
                return "Bilinmeyen durum";
        }
    }
}