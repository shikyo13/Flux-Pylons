package com.zerotheabsolute.quantumflux.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Server-side salted password verifier with legacy client-proof migration. */
public final class QFPasswordVerifier {

    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 60_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private QFPasswordVerifier() {}

    public static String create(String password) {
        if (!QFPasswordUtil.isValidPassword(password)) {
            throw new IllegalArgumentException("Invalid password");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$" + ENCODER.encodeToString(salt)
                + "$" + ENCODER.encodeToString(derived);
    }

    public static boolean verify(String password, String storedVerifier) {
        if (!QFPasswordUtil.isValidPassword(password) || storedVerifier == null) return false;

        // Worlds written by the previous protocol stored SHA-256(password).
        if (QFPasswordUtil.isClientProof(storedVerifier)) {
            String legacyProof = QFPasswordUtil.hash(password);
            return MessageDigest.isEqual(legacyProof.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    storedVerifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }

        Parsed parsed = parse(storedVerifier);
        if (parsed == null) return false;
        byte[] candidate = derive(password, parsed.salt(), parsed.iterations());
        return MessageDigest.isEqual(candidate, parsed.verifier());
    }

    public static boolean isValidStoredVerifier(String value) {
        return value != null && (value.isEmpty() || QFPasswordUtil.isClientProof(value) || parse(value) != null);
    }

    public static boolean isLegacy(String value) {
        return QFPasswordUtil.isClientProof(value);
    }

    private static Parsed parse(String value) {
        String[] parts = value.split("\\$", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) return null;
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 10_000 || iterations > 1_000_000) return null;
            byte[] salt = DECODER.decode(parts[2]);
            byte[] verifier = DECODER.decode(parts[3]);
            if (salt.length != SALT_BYTES || verifier.length != KEY_BITS / 8) return null;
            return new Parsed(iterations, salt, verifier);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2-HMAC-SHA256 is unavailable", exception);
        } finally {
            spec.clearPassword();
        }
    }

    private record Parsed(int iterations, byte[] salt, byte[] verifier) {}
}
