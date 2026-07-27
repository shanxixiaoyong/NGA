package sp.phone.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

public class DefaultSettingsContractTest {

    @Test
    public void preferenceDefaultsMatchStandardSettings() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File("src/main/res/xml/settings.xml"));

        assertDefault(document, "nga_domain", "1");
        assertDefault(document, "nightmode", "false");
        assertDefault(document, "key_night_mode_follow_system",
                Boolean.toString(Constants.NIGHT_MODE_FOLLOW_SYSTEM_DEFAULT));
        assertDefault(document, "use_solid_color_bg", "true");
        assertDefault(document, "enableNotification", "true");
        assertDefault(document, "notificationSound",
                Boolean.toString(Constants.NOTIFICATION_SOUND_DEFAULT));
        assertDefault(document, "material_theme", Constants.MATERIAL_THEME_DEFAULT);
        assertDefault(document, "sort_by_post", "false");
        assertDefault(document, "filter_sub_board", "false");
        assertDefault(document, "@string/pref_load_pic_strategy", "0");
        assertDefault(document, "@string/pref_load_avatar_strategy", "0");
        assertDefault(document, "showSignature", "false");
        assertDefault(document, "showColortxt", "false");
        assertDefault(document, "refresh_after_post_setting_mode", "true");
    }

    @Test
    public void removedPreferencesAreNotExposed() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File("src/main/res/xml/settings.xml"));

        assertMissingPreference(document, "left_hand");
        assertMissingPreference(document, "bottom_tab");
    }

    @Test
    public void sizeDefaultsMatchStandardSettings() {
        assertEquals(20, Constants.TOPIC_TITLE_SIZE_DEFAULT);
        assertEquals(100, Constants.AVATAR_SIZE_DEFAULT);
        assertEquals(60, Constants.EMOTICON_SIZE_DEFAULT);
        assertEquals(80, Constants.WEBVIEW_DEFAULT_TEXT_ZOOM);
    }

    @Test
    public void runtimeFallbacksMatchPreferenceDefaults() {
        assertFalse(Constants.NOTIFICATION_SOUND_DEFAULT);
        assertEquals("2", Constants.MATERIAL_THEME_DEFAULT);
        assertTrue(Constants.NIGHT_MODE_FOLLOW_SYSTEM_DEFAULT);
    }

    private static void assertDefault(Document document, String key, String expected) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (key.equals(element.getAttribute("android:key"))) {
                assertEquals(expected, element.getAttribute("android:defaultValue"));
                return;
            }
        }
        throw new AssertionError("Missing preference key: " + key);
    }

    private static void assertMissingPreference(Document document, String key) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (key.equals(element.getAttribute("android:key"))) {
                throw new AssertionError("Unexpected preference key: " + key);
            }
        }
    }
}
