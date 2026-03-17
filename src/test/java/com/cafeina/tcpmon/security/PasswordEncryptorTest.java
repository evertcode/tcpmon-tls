package com.cafeina.tcpmon.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncryptorTest {
    @TempDir
    Path tempDir;

    @Test
    void encryptAndDecryptRoundTrips() throws Exception {
        PasswordEncryptor encryptor = PasswordEncryptor.fromKeyFile(tempDir.resolve("db.key"));
        String encrypted = encryptor.encrypt("secret-password");
        assertEquals("secret-password", encryptor.decrypt(encrypted));
    }

    @Test
    void encryptedValueHasPrefix() throws Exception {
        PasswordEncryptor encryptor = PasswordEncryptor.fromKeyFile(tempDir.resolve("db.key"));
        String encrypted = encryptor.encrypt("changeit");
        assertTrue(encrypted.startsWith(PasswordEncryptor.ENCRYPTED_PREFIX));
    }

    @Test
    void twoEncryptionsProduceDifferentCiphertexts() throws Exception {
        PasswordEncryptor encryptor = PasswordEncryptor.fromKeyFile(tempDir.resolve("db.key"));
        String a = encryptor.encrypt("same");
        String b = encryptor.encrypt("same");
        assertNotEquals(a, b, "IV randomisation must produce distinct ciphertexts");
    }

    @Test
    void nullPassesThroughUnchanged() throws Exception {
        PasswordEncryptor encryptor = PasswordEncryptor.fromKeyFile(tempDir.resolve("db.key"));
        assertNull(encryptor.encrypt(null));
        assertNull(encryptor.decrypt(null));
    }

    @Test
    void legacyPlaintextReturnedAsIs() throws Exception {
        PasswordEncryptor encryptor = PasswordEncryptor.fromKeyFile(tempDir.resolve("db.key"));
        assertEquals("plaintext", encryptor.decrypt("plaintext"));
    }

    @Test
    void keyFileIsPersisted() throws Exception {
        Path keyFile = tempDir.resolve("db.key");
        PasswordEncryptor first = PasswordEncryptor.fromKeyFile(keyFile);
        String encrypted = first.encrypt("hello");

        PasswordEncryptor second = PasswordEncryptor.fromKeyFile(keyFile);
        assertEquals("hello", second.decrypt(encrypted));
    }

    @Test
    void keyFileIsCreatedOnFirstUse() throws Exception {
        Path keyFile = tempDir.resolve("db.key");
        PasswordEncryptor.fromKeyFile(keyFile);
        assertTrue(Files.exists(keyFile));
    }
}
