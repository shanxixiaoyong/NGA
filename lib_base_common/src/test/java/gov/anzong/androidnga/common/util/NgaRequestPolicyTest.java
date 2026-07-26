package gov.anzong.androidnga.common.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NgaRequestPolicyTest {

    @Test
    public void acceptsOnlyExactTrustedHttpsHosts() {
        assertTrue(NgaRequestPolicy.isTrustedHttps("https", "bbs.nga.cn"));
        assertTrue(NgaRequestPolicy.isTrustedHttps("HTTPS", "NGABBS.COM"));

        assertFalse(NgaRequestPolicy.isTrustedHttps("http", "bbs.nga.cn"));
        assertFalse(NgaRequestPolicy.isTrustedHttps("https", "bbs.nga.cn.example.com"));
        assertFalse(NgaRequestPolicy.isTrustedHttps("https", "evilnga.com"));
        assertFalse(NgaRequestPolicy.isTrustedHttps(null, "bbs.nga.cn"));
    }

    @Test
    public void detectsLegacyOfficialIdentity() {
        assertTrue(NgaRequestPolicy.isOfficialImpersonation("Nga_Official/573"));
        assertFalse(NgaRequestPolicy.isOfficialImpersonation(
                NgaRequestPolicy.DEFAULT_USER_AGENT));
    }
}
