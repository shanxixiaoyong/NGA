package gov.anzong.androidnga.gallery;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class ImageGalleryPolicyTest {

    @Test
    public void queryAndFragmentDoNotHideImageSuffix() {
        assertTrue(ImageGalleryPolicy.isImageUrl(
                "https://img.example/a/photo.JPG?width=1080#preview"));
        assertTrue(ImageGalleryPolicy.isImageUrl("https://img.example/a/photo.webp?x=1"));
        assertFalse(ImageGalleryPolicy.isImageUrl("https://img.example/a/photo.txt?x=.jpg"));
    }

    @Test
    public void clickedUrlResolvesToItsOwnGalleryEntry() {
        assertEquals(1, ImageGalleryPolicy.findIndex(
                Arrays.asList(
                        "https://img.example/a/one.jpg",
                        "https://IMG.EXAMPLE/a/two.PNG?cache=1"),
                "https://img.example/a/two.png?cache=1#image"));
    }

    @Test
    public void htmlEscapingAndEmptyEntriesAreHandledWithoutReordering() {
        assertArrayEquals(
                new String[]{
                        "https://img.example/a/one.jpg?x=1&amp;y=2",
                        "https://img.example/a/two.jpg"},
                ImageGalleryPolicy.copyUrls(
                        Arrays.asList(
                                "https://img.example/a/one.jpg?x=1&amp;y=2",
                                "",
                                null,
                                "https://img.example/a/two.jpg"),
                        "https://img.example/a/one.jpg?x=1&y=2"));
    }

    @Test
    public void clickedImageIsAddedWhenParserDidNotCollectIt() {
        assertArrayEquals(
                new String[]{"https://img.example/a/known.jpg", "https://img.example/a/new.jpg"},
                ImageGalleryPolicy.copyUrls(
                        Arrays.asList("https://img.example/a/known.jpg"),
                        "https://img.example/a/new.jpg"));
    }

    @Test
    public void thumbnailAndFullImageVariantsResolveToTheSameEntry() {
        assertEquals(1, ImageGalleryPolicy.findIndex(
                Arrays.asList(
                        "https://img.example/a/one.jpg",
                        "https://img.example/a/two.jpg.thumb.jpg"),
                "https://img.example/a/two.jpg"));
    }

    @Test
    public void samePathOnAnotherHostDoesNotStealTheClickedIndex() {
        assertEquals(-1, ImageGalleryPolicy.findIndex(
                Arrays.asList("https://cdn-a.example/a/photo.jpg"),
                "https://cdn-b.example/a/photo.jpg?cache=2"));
    }
}
