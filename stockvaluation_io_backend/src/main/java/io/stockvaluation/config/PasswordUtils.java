package io.stockvaluation.config;

import org.apache.commons.codec.digest.DigestUtils;

public class PasswordUtils {

    // Encode the password using SHA-256
    public static String encodePassword(String password) {
        return DigestUtils.sha256Hex(password);
    }

    // Check if the raw password matches the encoded password
    public static boolean matchPassword(String rawPassword, String encodedPassword) {
        // Encode the raw password and compare it to the stored hash
        String hashedRawPassword = encodePassword(rawPassword);
        return hashedRawPassword.equals(encodedPassword);
    }

}
