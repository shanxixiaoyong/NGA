package sp.phone.ui.fragment;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class OptionalTopLikedPageContractTest {
    @Test
    public void preferenceGatesTheExistingPagerMappingBeforeAnyPageIsCreated() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/sp/phone/ui/adapter/ArticlePagerAdapter.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("PreferenceUtils.getData(PreferenceKey.KEY_SHOW_NGA_TOP_LIKED_PAGE, true)"));
        assertTrue(source.contains("mHasTopLikedPage = isTopLikedPageEligible(param);"));
        assertTrue(source.contains("param.page = position + 1;"));
        assertTrue(source.contains("param.topLikedPage = false;"));
        assertTrue(source.contains("mHasTopLikedPage ? adapterPosition : adapterPosition + 1"));
        assertTrue(source.contains("mHasTopLikedPage ? serverPage : serverPage - 1"));
        assertTrue(source.contains("zeroBasedNormalPage + (mHasTopLikedPage ? 1 : 0)"));
        assertTrue(source.contains("Math.max(1, count) + (mHasTopLikedPage ? 1 : 0)"));
    }
}
