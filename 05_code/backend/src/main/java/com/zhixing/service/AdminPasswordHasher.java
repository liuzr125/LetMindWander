package com.zhixing.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Stores administrator passwords with a per-password salt; never stores plaintext. */
public final class AdminPasswordHasher {
    private static final int ITERATIONS = 180000, KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    private AdminPasswordHasher() { }
    public static String hash(String password) {
        try { byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
            return "pbkdf2-sha256$" + ITERATIONS + "$" + b64(salt) + "$" + b64(derive(password.toCharArray(), salt, ITERATIONS));
        } catch (Exception exception) { throw new IllegalStateException("Password hashing unavailable", exception); }
    }
    public static boolean matches(String password, String encoded) {
        try {
            String[] fields = encoded == null ? new String[0] : encoded.split("\\$", -1);
            if (fields.length != 4 || !"pbkdf2-sha256".equals(fields[0])) return false;
            int iterations = Integer.parseInt(fields[1]);
            byte[] expected = Base64.getDecoder().decode(fields[3]);
            byte[] actual = derive(password.toCharArray(), Base64.getDecoder().decode(fields[2]), iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ignored) { return false; }
    }
    private static byte[] derive(char[] password, byte[] salt, int iterations) throws Exception {
        KeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { ((PBEKeySpec) spec).clearPassword(); }
    }
    private static String b64(byte[] value) { return Base64.getEncoder().withoutPadding().encodeToString(value); }
}
