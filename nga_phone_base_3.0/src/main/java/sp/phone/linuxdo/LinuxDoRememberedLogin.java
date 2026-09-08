package sp.phone.linuxdo;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import gov.anzong.androidnga.base.util.ContextUtils;

/** Device-local, Android-Keystore encrypted login form memory. */
public final class LinuxDoRememberedLogin {

    private static final String PREFS = "linuxdo_remembered_login";
    private static final String CIPHERTEXT = "ciphertext";
    private static final String IV = "iv";
    private static final String AES_ALIAS = "nga_linuxdo_login_form_aes_v1";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final class Entry {
        public final String identifier;
        public final String password;

        Entry(String identifier, String password) {
            this.identifier = identifier;
            this.password = password;
        }
    }

    public static void save(String identifier, String password) {
        String safeIdentifier = identifier == null ? "" : identifier.trim();
        String safePassword = password == null ? "" : password;
        if (safeIdentifier.isEmpty() || safePassword.isEmpty()
                || safeIdentifier.length() > 320 || safePassword.length() > 1024) return;
        try {
            JSONObject value = new JSONObject();
            value.put("identifier", safeIdentifier);
            value.put("password", safePassword);
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(true), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
            preferences().edit()
                    .putString(IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .putString(CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .apply();
        } catch (Exception ignored) {
            // Remembering a login is optional and must never break a successful sign-in.
        }
    }

    public static Entry load() {
        SharedPreferences preferences = preferences();
        String encodedIv = preferences.getString(IV, "");
        String encodedCiphertext = preferences.getString(CIPHERTEXT, "");
        if (encodedIv == null || encodedIv.isEmpty()
                || encodedCiphertext == null || encodedCiphertext.isEmpty()) return null;
        try {
            SecretKey key = aesKey(false);
            if (key == null) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(
                    128, Base64.decode(encodedIv, Base64.NO_WRAP)));
            JSONObject value = new JSONObject(new String(cipher.doFinal(Base64.decode(
                    encodedCiphertext, Base64.NO_WRAP)), StandardCharsets.UTF_8));
            String identifier = value.optString("identifier", "").trim();
            String password = value.optString("password", "");
            if (identifier.isEmpty() || password.isEmpty()) return null;
            return new Entry(identifier, password);
        } catch (Exception ignored) {
            clear();
            return null;
        }
    }

    public static void clear() {
        preferences().edit().remove(IV).remove(CIPHERTEXT).apply();
    }

    private static SecretKey aesKey(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry entry = store.getEntry(AES_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        if (!create) return null;
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                AES_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static SharedPreferences preferences() {
        Context context = ContextUtils.getApplication();
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private LinuxDoRememberedLogin() {
    }
}
