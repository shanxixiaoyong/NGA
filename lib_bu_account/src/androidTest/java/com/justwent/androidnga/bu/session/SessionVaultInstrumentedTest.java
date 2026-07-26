package com.justwent.androidnga.bu.session;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SessionVaultInstrumentedTest {

    private Context context;
    private String accountId;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SessionVault.initialize(context);
        accountId = "__session_vault_test_" + System.nanoTime();
    }

    @After
    public void tearDown() {
        SessionVault.remove(accountId);
    }

    @Test
    public void credentialsAreEncryptedAndAccountScoped() throws Exception {
        String credential = "cid-test-value-" + System.nanoTime();

        assertTrue(SessionVault.put(accountId, credential));
        assertEquals(credential, SessionVault.get(accountId));
        assertNull(SessionVault.get(accountId + "-other"));

        File vaultFile = new File(
                context.getNoBackupFilesDir(), "account-sessions.properties");
        assertTrue(vaultFile.isFile());
        String serialized = new String(
                Files.readAllBytes(vaultFile.toPath()), StandardCharsets.UTF_8);
        assertFalse(serialized.contains(credential));

        SessionVault.remove(accountId);
        assertNull(SessionVault.get(accountId));
    }
}
