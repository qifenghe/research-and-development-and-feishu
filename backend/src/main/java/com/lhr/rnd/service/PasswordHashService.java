package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PasswordHashService {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException("AUTH_PASSWORD_REQUIRED", "密码不能为空");
        }
        var salt = new byte[16];
        secureRandom.nextBytes(salt);
        var derived = derive(password.toCharArray(), salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + encode(salt) + "$" + encode(derived);
    }

    public boolean matches(String password, String storedHash) {
        if (password == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        var parts = storedHash.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        try {
            var iterations = Integer.parseInt(parts[1]);
            var salt = Base64.getDecoder().decode(parts[2]);
            var expected = Base64.getDecoder().decode(parts[3]);
            var actual = derive(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception exception) {
            return false;
        }
    }

    private byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            var spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("密码哈希失败", exception);
        }
    }

    private String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }
}
