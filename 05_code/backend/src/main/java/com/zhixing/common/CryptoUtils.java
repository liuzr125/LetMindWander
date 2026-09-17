package com.zhixing.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {
    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoUtils() {}

    public static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    public static String seal(String value,String masterKey) {
        if (masterKey == null || masterKey.trim().length() < 16) throw new IllegalArgumentException("AI credential encryption key is not configured");
        try {
            byte[] iv=new byte[12];RANDOM.nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(sha256(masterKey),"AES"),new GCMParameterSpec(128,iv));
            return "enc:v1:"+Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Unable to encrypt credential",e); }
    }

    public static String open(String value,String masterKey) {
        if(value==null||!value.startsWith("enc:v1:"))return value;
        if(masterKey==null||masterKey.trim().length()<16)throw new IllegalStateException("AI credential encryption key is not configured");
        try {
            String[] parts=value.split(":",4);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(sha256(masterKey),"AES"),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[2])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[3])),StandardCharsets.UTF_8);
        } catch(Exception e){throw new IllegalStateException("Unable to decrypt credential",e);}
    }
}
