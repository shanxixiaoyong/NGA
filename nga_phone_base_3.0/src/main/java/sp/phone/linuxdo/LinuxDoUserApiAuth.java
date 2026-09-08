package sp.phone.linuxdo;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import gov.anzong.androidnga.base.util.ContextUtils;

/** Discourse User API Key authorization and app-private credential storage. */
public final class LinuxDoUserApiAuth {

    public static final String CALLBACK_SCHEME = "discourse";
    public static final String CALLBACK_HOST = "auth_redirect";
    private static final String CALLBACK_URL = CALLBACK_SCHEME + "://" + CALLBACK_HOST;
    private static final String PREFS = "linuxdo_user_api_auth";
    private static final String PREF_CLIENT_ID = "client_id";
    private static final String PREF_PENDING_NONCE = "pending_nonce";
    private static final String PREF_KEY_CIPHERTEXT = "key_ciphertext";
    private static final String PREF_KEY_IV = "key_iv";
    private static final String RSA_ALIAS = "nga_linuxdo_user_api_rsa_v1";
    private static final String AES_ALIAS = "nga_linuxdo_user_api_aes_v1";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile String sCachedApiKey;

    public enum Result {
        SUCCESS,
        INVALID_CALLBACK,
        EXPIRED_OR_UNEXPECTED,
        CRYPTO_ERROR
    }

    public static Uri createAuthorizationUri(boolean replaceExisting) throws Exception {
        if (replaceExisting) clearCredential();
        SharedPreferences preferences = preferences();
        String nonce = randomHex(16);
        preferences.edit().putString(PREF_PENDING_NONCE, nonce).apply();
        String publicKey = publicKeyPem(keyPair().getPublic());
        return Uri.parse(LinuxDoConstants.ORIGIN + "/user-api-key/new").buildUpon()
                .appendQueryParameter("scopes", "read,write")
                .appendQueryParameter("client_id", clientId())
                .appendQueryParameter("nonce", nonce)
                .appendQueryParameter("auth_redirect", CALLBACK_URL)
                .appendQueryParameter("application_name", "NGA")
                // Pin the protocol to the padding used by the Android Keystore decryptor.
                // Discourse currently defaults to PKCS#1, but making it explicit keeps the
                // callback compatible if the server's default changes in a later release.
                .appendQueryParameter("padding", "pkcs1")
                .appendQueryParameter("public_key", publicKey)
                .build();
    }

    public static Result acceptCallback(Uri callback) {
        if (callback == null
                || !CALLBACK_SCHEME.equalsIgnoreCase(callback.getScheme())
                || !CALLBACK_HOST.equalsIgnoreCase(callback.getHost())) {
            return Result.INVALID_CALLBACK;
        }
        String payload = callback.getQueryParameter("payload");
        String expectedNonce = preferences().getString(PREF_PENDING_NONCE, "");
        if (payload == null || payload.isEmpty() || expectedNonce == null
                || expectedNonce.isEmpty()) {
            return Result.EXPIRED_OR_UNEXPECTED;
        }
        try {
            byte[] encrypted = decodePayload(payload);
            KeyStore store = keyStore();
            PrivateKey privateKey = (PrivateKey) store.getKey(RSA_ALIAS, null);
            if (privateKey == null) return Result.CRYPTO_ERROR;
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            JSONObject result = new JSONObject(new String(
                    cipher.doFinal(encrypted), StandardCharsets.UTF_8));
            String nonce = result.optString("nonce", "");
            String apiKey = result.optString("key", "").trim();
            if (!constantTimeEquals(expectedNonce, nonce) || apiKey.isEmpty()
                    || apiKey.length() > 512) {
                return Result.EXPIRED_OR_UNEXPECTED;
            }
            storeCredential(apiKey);
            preferences().edit().remove(PREF_PENDING_NONCE).apply();
            return Result.SUCCESS;
        } catch (Exception error) {
            return Result.CRYPTO_ERROR;
        }
    }

    /**
     * Discourse emits standard Base64 with line breaks, while Android browsers may percent
     * decode '+' as a space and some redirect handlers rewrite the value to URL-safe Base64.
     * Accept those transport-level variants without relaxing the nonce/RSA checks below.
     */
    private static byte[] decodePayload(String payload) {
        String value = payload == null ? "" : payload.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("empty payload");
        try {
            return Base64.decode(value, Base64.DEFAULT);
        } catch (IllegalArgumentException ignored) {
            // Fall through to the URL-safe form used by a few Android intent brokers.
        }
        String compact = value.replaceAll("\\s", "");
        try {
            return Base64.decode(compact, Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (IllegalArgumentException ignored) {
            // Padding is optional in URL-safe query values.
            return Base64.decode(compact, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        }
    }

    public static String apiKey() {
        String cached = sCachedApiKey;
        if (cached != null) return cached;
        SharedPreferences preferences = preferences();
        String encodedCiphertext = preferences.getString(PREF_KEY_CIPHERTEXT, "");
        String encodedIv = preferences.getString(PREF_KEY_IV, "");
        if (encodedCiphertext == null || encodedCiphertext.isEmpty()
                || encodedIv == null || encodedIv.isEmpty()) return "";
        try {
            SecretKey key = aesKey(false);
            if (key == null) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(
                    128, Base64.decode(encodedIv, Base64.NO_WRAP)));
            String apiKey = new String(cipher.doFinal(Base64.decode(
                    encodedCiphertext, Base64.NO_WRAP)), StandardCharsets.UTF_8);
            sCachedApiKey = apiKey;
            return apiKey;
        } catch (Exception error) {
            clearCredential();
            return "";
        }
    }

    public static boolean hasCredential() {
        return !apiKey().isEmpty();
    }

    public static String clientId() {
        SharedPreferences preferences = preferences();
        String existing = preferences.getString(PREF_CLIENT_ID, "");
        if (existing != null && !existing.isEmpty()) return existing;
        String created = "nga-android-" + UUID.randomUUID().toString()
                .replace("-", "").toLowerCase(Locale.ROOT);
        preferences.edit().putString(PREF_CLIENT_ID, created).apply();
        return created;
    }

    public static void clearCredential() {
        sCachedApiKey = null;
        preferences().edit()
                .remove(PREF_KEY_CIPHERTEXT)
                .remove(PREF_KEY_IV)
                .remove(PREF_PENDING_NONCE)
                .apply();
        LinuxDoSessionState.setReady(false);
    }

    private static void storeCredential(String apiKey) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey(true), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
        preferences().edit()
                .putString(PREF_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(PREF_KEY_CIPHERTEXT,
                        Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .apply();
        sCachedApiKey = apiKey;
    }

    private static SecretKey aesKey(boolean create) throws Exception {
        KeyStore store = keyStore();
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

    private static KeyPair keyPair() throws Exception {
        KeyStore store = keyStore();
        if (store.containsAlias(RSA_ALIAS)) {
            PublicKey publicKey = store.getCertificate(RSA_ALIAS).getPublicKey();
            PrivateKey privateKey = (PrivateKey) store.getKey(RSA_ALIAS, null);
            if (publicKey != null && privateKey != null) return new KeyPair(publicKey, privateKey);
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
        generator.initialize(new KeyGenParameterSpec.Builder(
                RSA_ALIAS, KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .build());
        return generator.generateKeyPair();
    }

    private static KeyStore keyStore() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    private static String publicKeyPem(PublicKey key) {
        String encoded = Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP);
        StringBuilder builder = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
        for (int start = 0; start < encoded.length(); start += 64) {
            builder.append(encoded, start, Math.min(start + 64, encoded.length())).append('\n');
        }
        return builder.append("-----END PUBLIC KEY-----").toString();
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        StringBuilder result = new StringBuilder(bytes * 2);
        for (byte item : value) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return result.toString();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) return false;
        int difference = 0;
        for (int index = 0; index < left.length; index++) difference |= left[index] ^ right[index];
        return difference == 0;
    }

    private static SharedPreferences preferences() {
        Context context = ContextUtils.getApplication();
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private LinuxDoUserApiAuth() {
    }
}
