package com.justwent.androidnga.bu.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AccountSessionSnapshotTest {

    @Test
    public void authenticatedSnapshotIsImmutableAndCredentialSafe() {
        String credential = "secret-cid-value";
        AccountSessionSnapshot first = AccountSessionSnapshot.authenticated(
                "account-a",
                7L,
                "12345",
                "ngaPassportUid=12345; ngaPassportCid=" + credential);
        AccountSessionSnapshot sameIdentityWithReplacement =
                AccountSessionSnapshot.authenticated(
                        "account-a",
                        7L,
                        "12345",
                        "ngaPassportUid=12345; ngaPassportCid=replacement-secret");

        assertFalse(first.isAnonymous());
        assertEquals("account-a", first.getAccountId());
        assertEquals(7L, first.getSessionGeneration());
        assertEquals("12345", first.getUid());
        assertTrue(first.getCookieHeader().contains(credential));
        assertEquals(first, sameIdentityWithReplacement);
        assertEquals(first.hashCode(), sameIdentityWithReplacement.hashCode());
        assertFalse(first.toString().contains("account-a"));
        assertFalse(first.toString().contains("12345"));
        assertFalse(first.toString().contains(credential));
        assertFalse(first.toString().contains("replacement-secret"));
    }

    @Test
    public void anonymousSnapshotIsExplicitAndGenerationBound() {
        AccountSessionSnapshot first = AccountSessionSnapshot.anonymous(4L);
        AccountSessionSnapshot revokedGeneration = AccountSessionSnapshot.anonymous(5L);

        assertTrue(first.isAnonymous());
        assertEquals(AccountSessionSnapshot.ANONYMOUS_ACCOUNT_ID, first.getAccountId());
        assertEquals("", first.getUid());
        assertEquals("", first.getCookieHeader());
        assertNotEquals(first, revokedGeneration);
        assertEquals("AccountSessionSnapshot{anonymous}", first.toString());
    }
}
