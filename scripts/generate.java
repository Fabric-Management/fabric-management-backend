/**
 * Quick script to generate BCrypt password hash for platform admin
 * 
 * Usage:
 * 1. Update password variable below
 * 2. Run: javac -cp ".:path-to-spring-security-crypto.jar" generate-bcrypt-password.java
 * 3. Run: java -cp ".:path-to-spring-security-crypto.jar" generate-bcrypt-password
 * 
 * Or use online tool: https://bcrypt-generator.com/ (development only!)
 */

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class generate-bcrypt-password {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // ⚠️ CHANGE THIS PASSWORD
        String password = "admin123";
        
        String hash = encoder.encode(password);
        
        System.out.println("=====================================");
        System.out.println("Password: " + password);
        System.out.println("BCrypt Hash: " + hash);
        System.out.println("=====================================");
        System.out.println();
        System.out.println("Copy the hash to V019__create_platform_admin.sql");
    }
}

