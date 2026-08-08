package sp.phone.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class VersionUpgradeHelperMigrationTest {

    @Test
    public void missingValueRemainsMissingAndUsesNewDefault() {
        assertNull(VersionUpgradeHelper.migrateImageDomainModeValue(null, false));
    }

    @Test
    public void oldModesMapToNewStorageContract() {
        assertEquals("0", VersionUpgradeHelper.migrateImageDomainModeValue("0", false));
        assertEquals("2", VersionUpgradeHelper.migrateImageDomainModeValue("1", false));
        assertEquals("3", VersionUpgradeHelper.migrateImageDomainModeValue("2", false));
    }

    @Test
    public void corruptValueFallsBackToAuto() {
        assertEquals("0", VersionUpgradeHelper.migrateImageDomainModeValue("bad", false));
        assertEquals("0", VersionUpgradeHelper.migrateImageDomainModeValue("3", false));
        assertEquals("0", VersionUpgradeHelper.migrateImageDomainModeValue("", false));
    }

    @Test
    public void completedMigrationDoesNotRemapNewValues() {
        assertEquals("2", VersionUpgradeHelper.migrateImageDomainModeValue("2", true));
        assertEquals("3", VersionUpgradeHelper.migrateImageDomainModeValue("3", true));
    }
}
