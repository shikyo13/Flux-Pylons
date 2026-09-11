package com.zerotheabsolute.quantumflux.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class QFPasswordUtil {

    private QFPasswordUtil() {}

    /**
     * Legacy SHA-256 proof used only to migrate worlds from the original protocol.
     */
    public static String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static boolean isClientProof(String proof) {
        if (proof == null || proof.length() != 64) return false;
        for (int i = 0; i < proof.length(); i++) {
            char c = proof.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    public static boolean isValidPassword(String password) {
        if (password == null || password.isBlank()) return false;
        int length = password.codePointCount(0, password.length());
        if (length < 4 || password.length() > 64) return false;
        return password.codePoints().noneMatch(codePoint -> Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT);
    }
}
