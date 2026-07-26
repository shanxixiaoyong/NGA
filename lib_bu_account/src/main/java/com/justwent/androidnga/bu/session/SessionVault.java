package com.justwent.androidnga.bu.session;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Base64;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Small account-scoped credential vault.
 *
 * <p>The database contains account metadata only.  CIDs are encrypted with a
 * non-exportable Android Keystore AES-GCM key and written below
 * {@link Context#getNoBackupFilesDir()}, so the session cannot be restored from
 * an app backup.  The account id is authenticated as AES-GCM AAD.</p>
 */
public final class SessionVault {

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "nga_session_v1";
    private static final String FORMAT_VERSION = "v1";
    private static final String KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final String FILE_NAME = "account-sessions.properties";

    private static final Map<String, String> ENTRIES = new HashMap<>();
    private static File storageFile;
    private static boolean initialized;

    private SessionVault() {
    }

    /** Initializes the vault once with an application context. */
    public static synchronized void initialize(Context context) {
        if (initialized) {
            return;
        }
        Context appContext = context.getApplicationContext();
        File directory = appContext.getNoBackupFilesDir();
        storageFile = new File(directory, FILE_NAME);
        loadEntries();
        initialized = true;
    }

    public static synchronized boolean isInitialized() {
        return initialized;
    }

    /** Stores a credential for one account. Empty values are never persisted. */
    public static synchronized boolean put(String accountId, String credential) {
        if (!isValidAccountId(accountId) || credential == null || credential.isEmpty()) {
            return false;
        }
        if (!initialized) {
            return false;
        }
        String previous = ENTRIES.get(accountId);
        try {
            ENTRIES.put(accountId, encrypt(accountId, credential));
            persistEntries();
            return true;
        } catch (GeneralSecurityException | java.io.IOException ignored) {
            // A failed write must not replace a previously usable session.
            // The newly encrypted value is discarded; the caller must retry.
            if (previous == null) {
                ENTRIES.remove(accountId);
            } else {
                ENTRIES.put(accountId, previous);
            }
            return false;
        }
    }

    @Nullable
    public static synchronized String get(String accountId) {
        if (!initialized || !isValidAccountId(accountId)) {
            return null;
        }
        String encoded = ENTRIES.get(accountId);
        if (encoded == null) {
            return null;
        }
        try {
            return decrypt(accountId, encoded);
        } catch (GeneralSecurityException | IllegalArgumentException ignored) {
            // A corrupt entry is unusable; do not fall back to plaintext.
            ENTRIES.remove(accountId);
            try {
                persistEntries();
            } catch (java.io.IOException ignoredPersist) {
                // Keep the in-memory entry removed and retry persistence later.
                deleteStorageFile();
            }
            return null;
        }
    }

    public static synchronized void remove(String accountId) {
        if (!initialized || !isValidAccountId(accountId)) {
            return;
        }
        if (ENTRIES.remove(accountId) != null) {
            try {
                persistEntries();
            } catch (java.io.IOException ignored) {
                // Fail closed: do not leave a removable session recoverable on disk.
                deleteStorageFile();
            }
        }
    }

    public static synchronized void clear() {
        ENTRIES.clear();
        if (initialized) {
            try {
                persistEntries();
            } catch (java.io.IOException ignored) {
                // Keep the in-memory vault empty even if storage is unavailable.
                deleteStorageFile();
            }
        }
    }

    private static boolean isValidAccountId(String accountId) {
        if (accountId == null || accountId.isEmpty() || accountId.length() > 128) {
            return false;
        }
        for (int i = 0; i < accountId.length(); i++) {
            if (Character.isISOControl(accountId.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static SecretKey getOrCreateKey() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        try {
            keyStore.load(null);
        } catch (java.io.IOException | java.security.cert.CertificateException e) {
            throw new GeneralSecurityException("Unable to load Android Keystore", e);
        }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            java.security.Key key = keyStore.getKey(KEY_ALIAS, null);
            if (key instanceof SecretKey) {
                return (SecretKey) key;
            }
            throw new GeneralSecurityException("Unexpected session key type");
        }

        KeyGenerator generator = KeyGenerator.getInstance(KEY_ALGORITHM, KEYSTORE_PROVIDER);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static String encrypt(String accountId, String credential) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        cipher.updateAAD(aad(accountId));
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(credential.getBytes(StandardCharsets.UTF_8));
        return FORMAT_VERSION + ":" + Base64.encodeToString(iv, Base64.NO_WRAP)
                + ":"
                + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
    }

    private static String decrypt(String accountId, String encoded) throws GeneralSecurityException {
        String[] parts = encoded.split(":", -1);
        if (parts.length != 3 || !FORMAT_VERSION.equals(parts[0])) {
            throw new GeneralSecurityException("Invalid session entry");
        }
        byte[] iv = Base64.decode(parts[1], Base64.DEFAULT);
        byte[] ciphertext = Base64.decode(parts[2], Base64.DEFAULT);
        if (iv.length != IV_BYTES || ciphertext.length == 0) {
            throw new GeneralSecurityException("Invalid session entry size");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(aad(accountId));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private static byte[] aad(String accountId) {
        return (FORMAT_VERSION + "\u0000" + accountId).getBytes(StandardCharsets.UTF_8);
    }

    private static void loadEntries() {
        ENTRIES.clear();
        if (storageFile == null || !storageFile.isFile()) {
            return;
        }
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(storageFile)) {
            properties.load(input);
            for (String accountId : properties.stringPropertyNames()) {
                String value = properties.getProperty(accountId);
                if (isValidAccountId(accountId) && value != null && !value.isEmpty()) {
                    ENTRIES.put(accountId, value);
                }
            }
        } catch (java.io.IOException ignored) {
            ENTRIES.clear();
        }
    }

    private static void persistEntries() throws java.io.IOException {
        if (storageFile == null) {
            throw new java.io.IOException("Vault is not initialized");
        }
        File parent = storageFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new java.io.IOException("Unable to create vault directory");
        }
        File temporary = new File(parent, storageFile.getName() + ".tmp");
        Properties properties = new Properties();
        properties.putAll(ENTRIES);
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            properties.store(output, "NGA account session vault v1");
            output.getFD().sync();
        }
        try {
            // Os.rename replaces the destination atomically on Android/Linux.
            Os.rename(temporary.getAbsolutePath(), storageFile.getAbsolutePath());
        } catch (ErrnoException error) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            throw new java.io.IOException("Unable to atomically replace vault", error);
        }
    }

    private static void deleteStorageFile() {
        if (storageFile != null) {
            //noinspection ResultOfMethodCallIgnored
            storageFile.delete();
        }
    }
}
