package com.cafeina.tcpmon.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

/**
 * Encrypts and decrypts sensitive string values (e.g. keystore passwords) using
 * AES-256-GCM. The encryption key is auto-generated on first use and persisted
 * to a key file inside the sessions directory.
 *
 * <p>Encrypted values are stored with an {@code enc:} prefix so that legacy
 * plaintext values in existing databases are recognised and returned as-is
 * without failing.
 */
public final class PasswordEncryptor {
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    static final String ENCRYPTED_PREFIX = "enc:";

    private final SecretKeySpec secretKey;
    private final SecureRandom random = new SecureRandom();

    private PasswordEncryptor(byte[] keyBytes) {
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Loads the encryptor from an existing key file, or generates a new
     * 256-bit key and writes it to {@code keyFile} when the file does not exist.
     */
    public static PasswordEncryptor fromKeyFile(Path keyFile) throws IOException {
        if (Files.exists(keyFile)) {
            byte[] encoded = Files.readAllBytes(keyFile);
            return new PasswordEncryptor(Base64.getDecoder().decode(encoded));
        }
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(key));
        restrictPermissions(keyFile);
        return new PasswordEncryptor(key);
    }

    /** Encrypts {@code plaintext} and returns a value with the {@code enc:} prefix. */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[GCM_IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_BYTES);
            System.arraycopy(ciphertext, 0, combined, GCM_IV_BYTES, ciphertext.length);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt password", e);
        }
    }

    /**
     * Decrypts a value produced by {@link #encrypt}. Values without the
     * {@code enc:} prefix are returned unchanged (legacy plaintext support).
     */
    public String decrypt(String value) {
        if (value == null || !value.startsWith(ENCRYPTED_PREFIX)) {
            return value;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(value.substring(ENCRYPTED_PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt password", e);
        }
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystem — best-effort only
        }
    }
}
