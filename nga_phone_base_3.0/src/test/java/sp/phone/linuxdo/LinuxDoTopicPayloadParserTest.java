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
                + "{\"id\":2,\"name\":\"Linux\"}]}]}}";
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
}
