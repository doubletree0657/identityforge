package io.github.doubletree.iam.authentication.infrastructure.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SecretEncryptionService {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHERTEXT_PREFIX = "v1:";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretEncryptionService(@Value("${iam.security.secret-encryption-key}") String encodedKey) {
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("Secret encryption key must be 128, 192, or 256 bits");
        }
        this.key = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        Arrays.fill(keyBytes, (byte) 0);
    }

    public String encrypt(String plaintext) {
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();

            return CIPHERTEXT_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt secret", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (!ciphertext.startsWith(CIPHERTEXT_PREFIX)) {
            throw new IllegalArgumentException("Unsupported encrypted secret format");
        }

        byte[] payload = Base64.getDecoder().decode(ciphertext.substring(CIPHERTEXT_PREFIX.length()));
        byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_BYTES);
        byte[] encrypted = Arrays.copyOfRange(payload, GCM_IV_BYTES, payload.length);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt secret", exception);
        } finally {
            Arrays.fill(payload, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(encrypted, (byte) 0);
        }
    }
}
