package sp.phone.linuxdo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class LinuxDoTopicPayloadParserTest {

    @Test
    public void decodesCategoryAndLatestTopicFixtures() {
        String categoriesJson = "{\"category_list\":{\"categories\":["
                + "{\"id\":1,\"name\":\"开发调优\",\"subcategory_list\":["
                + "{\"id\":2,\"name\":\"Linux\",\"uploaded_logo\":"
                + "{\"url\":\"//linuxdo-uploads.s3.ldstatic.com/linux.png\"}}]}]}}";
        String latestJson = "{\"users\":[{\"id\":7,\"username\":\"alice\","
                + "\"name\":\"Alice\"}],\"topic_list\":{\"topics\":[{"
                + "\"id\":42,\"category_id\":2,\"title\":\"Hello Linux\","
                + "\"posts_count\":6,\"created_at\":\"2026-08-10T10:00:00Z\","
                + "\"bumped_at\":\"2026-08-11T11:12:13Z\","
                + "\"tags\":[\"人工智能\",{\"name\":\"纯水\"}],"
                + "\"posters\":[{\"user_id\":7}],"
                + "\"last_poster_username\":\"bob\"}]}}";

        Map<Integer, String> categories =
                LinuxDoTopicPayloadParser.parseCategories(categoriesJson);
        List<LinuxDoTopicPayloadParser.TopicRecord> topics =
                LinuxDoTopicPayloadParser.parseTopics(latestJson, categories);

        assertEquals("Linux", categories.get(2));
        assertEquals(1, topics.size());
        LinuxDoTopicPayloadParser.TopicRecord topic = topics.get(0);
        assertEquals(42, topic.id);
        assertEquals("Linux", topic.categoryName);
        assertEquals("https://linuxdo-uploads.s3.ldstatic.com/linux.png",
                LinuxDoTopicPayloadParser.parseCategoryRecords(categoriesJson)
                        .get(2).iconUrl);
        assertEquals("Hello Linux", topic.title);
        assertEquals(5, topic.replyCount);
        assertEquals("Alice", topic.author);
        assertEquals("bob", topic.lastPoster);
        assertEquals("#人工智能  #纯水", topic.tags);
        assertEquals((int) Instant.parse("2026-08-11T11:12:13Z").getEpochSecond(),
                topic.lastPostedAt);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPayloadWithoutTopicList() {
        LinuxDoTopicPayloadParser.parseTopics("{}", java.util.Collections.emptyMap());
    }

    @Test
    public void derivesOnlyExplicitTrustVisibility() {
        String json = "{\"category_list\":{\"categories\":["
                + "{\"id\":1,\"name\":\"公开\",\"read_restricted\":false},"
                + "{\"id\":2,\"name\":\"一级\",\"read_restricted\":true,"
                + "\"group_permissions\":[{\"group_name\":\"trust_level_1\"}]},"
                + "{\"id\":3,\"name\":\"未知\",\"read_restricted\":true}]}}";
        Map<Integer, LinuxDoTopicPayloadParser.CategoryRecord> records =
                LinuxDoTopicPayloadParser.parseCategoryRecords(json);
        assertEquals(null, records.get(1).visibility);
        assertEquals("Lv1", records.get(2).visibility);
        assertEquals(null, records.get(3).visibility);
    }

    @Test
    public void labelsExplicitLevelPartitionsWithTheirParentBoard() {
        String json = "{\"category_list\":{\"categories\":["
                + "{\"id\":10,\"name\":\"开发调优\",\"subcategory_list\":["
                + "{\"id\":11,\"name\":\"公开区\",\"read_restricted\":false},"
                + "{\"id\":12,\"name\":\"LV2区\",\"read_restricted\":true},"
                + "{\"id\":13,\"name\":\"内部\",\"read_restricted\":true,"
                + "\"minimum_required_trust_level\":1}]}]}}";
        Map<Integer, LinuxDoTopicPayloadParser.CategoryRecord> records =
                LinuxDoTopicPayloadParser.parseCategoryRecords(json);

        assertEquals("开发调优", records.get(11).name);
        assertEquals(null, records.get(11).visibility);
        assertEquals("开发调优", records.get(12).name);
        assertEquals("Lv2", records.get(12).visibility);
        assertEquals("开发调优", records.get(13).name);
        assertEquals("Lv1", records.get(13).visibility);
    }

    @Test
    public void stripsExplicitLevelSuffixFromStandaloneBoardName() {
        String json = "{\"category_list\":{\"categories\":["
                + "{\"id\":20,\"name\":\"开发调优 - LV3区\","
                + "\"read_restricted\":true}]}}";
        Map<Integer, LinuxDoTopicPayloadParser.CategoryRecord> records =
                LinuxDoTopicPayloadParser.parseCategoryRecords(json);

        assertEquals("开发调优", records.get(20).name);
        assertEquals("Lv3", records.get(20).visibility);
    }

    @Test
    public void decodesLinuxDoSiteCategoryTableWithLevelChildren() {
        String json = "{\"categories\":["
                + "{\"id\":4,\"name\":\"开发调优\",\"slug\":\"develop\","
                + "\"read_restricted\":false,\"subcategory_ids\":[20,31,88]},"
                + "{\"id\":20,\"name\":\"开发调优, Lv1\",\"slug\":\"develop-lv1\","
                + "\"parent_category_id\":4,\"read_restricted\":true},"
                + "{\"id\":31,\"name\":\"开发调优, Lv2\",\"slug\":\"develop-lv2\","
                + "\"parent_category_id\":4,\"read_restricted\":true},"
                + "{\"id\":88,\"name\":\"开发调优, Lv3\",\"slug\":\"develop-lv3\","
                + "\"parent_category_id\":4,\"read_restricted\":true}]}";
        Map<Integer, LinuxDoTopicPayloadParser.CategoryRecord> records =
                LinuxDoTopicPayloadParser.parseCategoryRecords(json);

        assertEquals("开发调优", records.get(4).name);
        assertEquals(null, records.get(4).visibility);
        assertEquals("开发调优", records.get(20).name);
        assertEquals("Lv1", records.get(20).visibility);
        assertEquals("开发调优", records.get(31).name);
        assertEquals("Lv2", records.get(31).visibility);
        assertEquals("开发调优", records.get(88).name);
        assertEquals("Lv3", records.get(88).visibility);
    }

    @Test
    public void carriesCategoryVisibilityIntoTopicRows() {
        String categoriesJson = "{\"categories\":["
                + "{\"id\":4,\"name\":\"开发调优\",\"slug\":\"develop\"},"
                + "{\"id\":31,\"name\":\"开发调优, Lv2\","
                + "\"slug\":\"develop-lv2\",\"parent_category_id\":4,"
                + "\"read_restricted\":true}]}";
        String latestJson = "{\"topic_list\":{\"topics\":[{"
                + "\"id\":42,\"category_id\":31,\"title\":\"level topic\","
                + "\"posts_count\":1}]}}";

        Map<Integer, LinuxDoTopicPayloadParser.CategoryRecord> records =
                LinuxDoTopicPayloadParser.parseCategoryRecords(categoriesJson);
        List<LinuxDoTopicPayloadParser.TopicRecord> topics =
                LinuxDoTopicPayloadParser.parseTopicsWithCategoryRecords(latestJson, records);

        assertEquals(1, topics.size());
        assertEquals("开发调优", topics.get(0).categoryName);
        assertEquals("Lv2", topics.get(0).visibility);
        assertEquals(4, topics.get(0).boardId);
        assertEquals(31, topics.get(0).categoryId);
    }

    @Test
    public void usesOneBlockingBoardIdAcrossPublicAndLevelPartitions() {
        String categoriesJson = "{\"categories\":["
                + "{\"id\":4,\"name\":\"搞七捻三\",\"slug\":\"random\"},"
                + "{\"id\":20,\"name\":\"公开区\",\"slug\":\"public\","
                + "\"parent_category_id\":4},"
                + "{\"id\":31,\"name\":\"搞七捻三, Lv1\","
                + "\"slug\":\"random-lv1\",\"parent_category_id\":4}]}";
        String latestJson = "{\"topic_list\":{\"topics\":["
                + "{\"id\":41,\"category_id\":20,\"title\":\"public\",\"posts_count\":1},"
                + "{\"id\":42,\"category_id\":31,\"title\":\"level\",\"posts_count\":1}]}}";

        List<LinuxDoTopicPayloadParser.TopicRecord> topics =
                LinuxDoTopicPayloadParser.parseTopicsWithCategoryRecords(latestJson,
                        LinuxDoTopicPayloadParser.parseCategoryRecords(categoriesJson));

        assertEquals(2, topics.size());
        assertEquals(4, topics.get(0).boardId);
        assertEquals(4, topics.get(1).boardId);
    }
}
