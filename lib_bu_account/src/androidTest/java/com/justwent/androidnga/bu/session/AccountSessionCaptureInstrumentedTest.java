package com.justwent.androidnga.bu.session;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.justwent.androidnga.bu.UserManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import gov.anzong.androidnga.db.AppDatabase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AccountSessionCaptureInstrumentedTest {

    private String firstUid;
    private String secondUid;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppDatabase.init(context);
        UserManager.INSTANCE.initialize(context);
        long suffix = System.nanoTime();
        firstUid = "session-test-a-" + suffix;
        secondUid = "session-test-b-" + suffix;
        assertTrue(UserManager.INSTANCE.addUser(firstUid, "cid-a-" + suffix, "A"));
        assertTrue(UserManager.INSTANCE.addUser(secondUid, "cid-b-" + suffix, "B"));
    }

    @After
    public void tearDown() {
        removeTestUser(firstUid);
        removeTestUser(secondUid);
    }

    @Test
    public void captureStaysBoundAcrossSwitchAndCredentialReplacement() {
        UserManager.INSTANCE.setActiveIndex(indexOf(firstUid));
        AccountSessionSnapshot first = UserManager.INSTANCE.captureActiveSession();

        assertFalse(first.isAnonymous());
        assertEquals(firstUid, first.getUid());
        assertTrue(UserManager.INSTANCE.isSessionCurrent(first));

        UserManager.INSTANCE.setActiveIndex(indexOf(secondUid));
        AccountSessionSnapshot second = UserManager.INSTANCE.captureActiveSession();

        assertFalse(UserManager.INSTANCE.isSessionCurrent(first));
        assertTrue(second.getSessionGeneration() > first.getSessionGeneration());
        assertEquals(secondUid, second.getUid());
        assertTrue(second.getCookieHeader().contains("cid-b-"));

        assertTrue(UserManager.INSTANCE.addUser(secondUid, "cid-b-replaced", "B"));
        AccountSessionSnapshot replaced = UserManager.INSTANCE.captureActiveSession();

        assertFalse(UserManager.INSTANCE.isSessionCurrent(second));
        assertTrue(replaced.getSessionGeneration() > second.getSessionGeneration());
        assertTrue(replaced.getCookieHeader().contains("cid-b-replaced"));
        assertFalse(second.getCookieHeader().contains("cid-b-replaced"));
    }

    @Test
    public void revokeInvalidatesOnlyTheCapturedGeneration() {
        UserManager.INSTANCE.setActiveIndex(indexOf(firstUid));
        AccountSessionSnapshot captured = UserManager.INSTANCE.captureActiveSession();

        assertTrue(UserManager.INSTANCE.revokeSession(captured));
        assertFalse(UserManager.INSTANCE.isSessionCurrent(captured));
        assertFalse(UserManager.INSTANCE.revokeSession(captured));

        AccountSessionSnapshot recaptured = UserManager.INSTANCE.captureActiveSession();
        assertTrue(recaptured.getSessionGeneration() > captured.getSessionGeneration());
        assertTrue(UserManager.INSTANCE.isSessionCurrent(recaptured));
    }

    private int indexOf(String uid) {
        for (int index = 0; index < UserManager.INSTANCE.getUserList().size(); index++) {
            if (uid.equals(UserManager.INSTANCE.getUserList().get(index).getUserId())) {
                return index;
            }
        }
        throw new AssertionError("Missing test user");
    }

    private void removeTestUser(String uid) {
        int index = -1;
        for (int current = 0; current < UserManager.INSTANCE.getUserList().size(); current++) {
            if (uid.equals(UserManager.INSTANCE.getUserList().get(current).getUserId())) {
                index = current;
                break;
            }
        }
        if (index >= 0) {
            UserManager.INSTANCE.removeUser(index);
        }
    }
}
