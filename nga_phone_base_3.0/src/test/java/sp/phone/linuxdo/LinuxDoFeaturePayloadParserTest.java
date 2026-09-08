package sp.phone.linuxdo;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LinuxDoFeaturePayloadParserTest {

    @Test
    public void decodesSearchTargets() {
        String json = "{\"topics\":[{\"id\":7,\"title\":\"原生搜索\"}],"
                + "\"posts\":[{\"topic_id\":7,\"post_number\":4,"
                + "\"username\":\"alice\",\"blurb\":\"<b>命中</b>\","
                + "\"created_at\":\"2026-08-16T00:00:00Z\"}]}";
        List<LinuxDoFeaturePayloadParser.SearchRow> rows =
                LinuxDoFeaturePayloadParser.parseSearch(json);
        assertEquals(1, rows.size());
        assertEquals(7, rows.get(0).topicId);
        assertEquals(4, rows.get(0).postNumber);
        assertEquals("命中", rows.get(0).excerpt);
    }

    @Test
    public void decodesUsersAndCategoriesWithoutTopicRows() {
        String json = "{\"users\":[{\"id\":4,\"username\":\"alice\","
                + "\"name\":\"Alice\",\"trust_level\":2}],"
                + "\"categories\":[{\"id\":9,\"name\":\"开发调优\","
                + "\"slug\":\"dev\",\"topic_count\":12}]}";
        List<LinuxDoFeaturePayloadParser.SearchRow> users =
                LinuxDoFeaturePayloadParser.parseSearch(json,
                        LinuxDoRepository.SearchOrder.RELEVANCE,
                        LinuxDoRepository.SearchScope.USERS);
        assertEquals(1, users.size());
        assertEquals(LinuxDoFeaturePayloadParser.SearchRow.Kind.USER, users.get(0).kind);
        assertEquals("alice", users.get(0).username);

        List<LinuxDoFeaturePayloadParser.SearchRow> categories =
                LinuxDoFeaturePayloadParser.parseSearch(json,
                        LinuxDoRepository.SearchOrder.RELEVANCE,
                        LinuxDoRepository.SearchScope.CATEGORIES);
        assertEquals(1, categories.size());
        assertEquals(LinuxDoFeaturePayloadParser.SearchRow.Kind.CATEGORY, categories.get(0).kind);
        assertEquals("dev", categories.get(0).slug);
    }

    @Test
    public void categorySearchWalksNestedSiteCategories() {
        String json = "{\"category_list\":{\"categories\":["
                + "{\"id\":1,\"name\":\"父板块\",\"slug\":\"parent\","
                + "\"subcategory_list\":[{\"id\":2,\"name\":\"子板块\","
                + "\"slug\":\"child\",\"topic_count\":4}]}]}}";
        List<LinuxDoFeaturePayloadParser.SearchRow> rows =
                LinuxDoFeaturePayloadParser.parseCategorySearch(json, "child");
        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).categoryId);
        assertEquals(LinuxDoFeaturePayloadParser.SearchRow.Kind.CATEGORY, rows.get(0).kind);
    }

    @Test
    public void categorySearchAcceptsDiscourseObjectChildLists() {
        String json = "{\"category_list\":{\"categories\":["
                + "{\"id\":1,\"name\":\"父\",\"subcategory_list\":{"
                + "\"categories\":[{\"id\":2,\"name\":\"子\",\"slug\":\"child\"}]}}]}}";
        List<LinuxDoFeaturePayloadParser.SearchRow> rows =
                LinuxDoFeaturePayloadParser.parseCategorySearch(json, "child");
        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).categoryId);
    }

    @Test
    public void categoryDirectoryKeepsParentAndLevelPartitionDistinct() {
        String json = "{\"category_list\":{\"categories\":["
                + "{\"id\":1,\"name\":\"开发调优\",\"slug\":\"dev\","
                + "\"subcategory_list\":[{\"id\":2,"
                + "\"name\":\"开发调优, Lv1\",\"slug\":\"dev-lv1\","
                + "\"group_permissions\":[{\"group_name\":\"trust_level_1\"}]}]}]}}";
        List<LinuxDoFeaturePayloadParser.CategoryRow> rows =
                LinuxDoFeaturePayloadParser.parseCategoryDirectory(json);
        assertEquals(2, rows.size());
        LinuxDoFeaturePayloadParser.CategoryRow child = rows.get(1);
        assertEquals(1, child.parentId);
        assertEquals("开发调优", child.name);
        assertEquals("开发调优", child.parentName);
        assertEquals("Lv1", child.visibility);
        assertEquals("dev/dev-lv1", child.slugPath);
    }

    @Test
    public void categoryDirectoryAcceptsFlatParentIds() {
        String json = "{\"categories\":["
                + "{\"id\":1,\"name\":\"父\",\"slug\":\"parent\"},"
                + "{\"id\":2,\"name\":\"子\",\"slug\":\"child\","
                + "\"parent_category_id\":1,\"minimum_required_trust_level\":2}]}";
        List<LinuxDoFeaturePayloadParser.CategoryRow> rows =
                LinuxDoFeaturePayloadParser.parseCategoryDirectory(json);
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(1).parentId);
        assertEquals("父", rows.get(1).parentName);
        assertEquals("Lv2", rows.get(1).visibility);
    }

    @Test
    public void topicCreatedOrderUsesTopicTimestamp() {
        String json = "{\"topics\":[{\"id\":7,\"title\":\"原生搜索\","
                + "\"created_at\":\"2026-08-10T00:00:00Z\"}],"
                + "\"posts\":[{\"topic_id\":7,\"post_number\":4,"
                + "\"created_at\":\"2026-08-16T00:00:00Z\"}]}";
        LinuxDoFeaturePayloadParser.SearchRow row = LinuxDoFeaturePayloadParser.parseSearch(
                json, LinuxDoRepository.SearchOrder.TOPIC_CREATED).get(0);
        assertEquals((int) java.time.Instant.parse("2026-08-10T00:00:00Z").getEpochSecond(),
                row.createdAt);
    }

    @Test
    public void decodesNotificationAndReadState() {
        String json = "{\"notification_types\":{\"replied\":5},\"notifications\":[{"
                + "\"id\":9,\"notification_type\":5,\"read\":false,"
                + "\"topic_id\":7,\"post_number\":3,"
                + "\"data\":{\"topic_title\":\"回复通知\",\"display_username\":\"bob\"}}]}";
        LinuxDoFeaturePayloadParser.NotificationRow row =
                LinuxDoFeaturePayloadParser.parseNotifications(json).get(0);
        assertEquals("replied", row.type);
        assertEquals(3, row.postNumber);
        assertFalse(row.read);
    }

    @Test
    public void notificationFallbacksKeepUnknownAndStringDataNavigable() {
        String json = "{\"notifications\":[{\"id\":11,\"type\":\"custom_event\","
                + "\"data\":\"{\\\"topic_id\\\":8,\\\"post_number\\\":6,"
                + "\\\"topic_title\\\":\\\"主题\\\",\\\"url\\\":\\\"/t/8/6\\\"}\","
                + "\"read\":true}]}";
        LinuxDoFeaturePayloadParser.NotificationRow row =
                LinuxDoFeaturePayloadParser.parseNotifications(json).get(0);
        assertEquals("custom_event", row.type);
        assertEquals(8, row.topicId);
        assertEquals(6, row.postNumber);
        assertEquals("/t/8/6", row.targetUrl);
        assertTrue(row.read);
    }

    @Test
    public void notificationTypesAcceptNumericKeyMap() {
        String json = "{\"notification_types\":{\"5\":\"replied\"},"
                + "\"notifications\":[{\"id\":1,\"notification_type\":5}]}";
        assertEquals("replied",
                LinuxDoFeaturePayloadParser.parseNotifications(json).get(0).type);
    }

    @Test
    public void boostNotificationKeepsDistinctPresentation() {
        String json = "{\"notifications\":[{\"id\":12,\"data\":{"
                + "\"event\":\"discourse_boosts_boosted\",\"topic_title\":\"主题\"}}]}";
        LinuxDoFeaturePayloadParser.NotificationRow row =
                LinuxDoFeaturePayloadParser.parseNotifications(json).get(0);
        assertEquals("Boost", LinuxDoNotificationPresentation.label(row.type));
        assertEquals("🚀", LinuxDoNotificationPresentation.icon(row.type));
    }

    @Test
    public void currentNotificationFallbacksKeepTopicActorAndAvatar() {
        String json = "{\"notifications\":["
                + "{\"id\":25,\"notification_type\":25,\"topic_id\":7,"
                + "\"acting_username\":\"alice\","
                + "\"acting_user_avatar_template\":\"/user_avatar/linux.do/alice/{size}/1.png\","
                + "\"data\":{\"topic_title\":\"表情主题\"}},"
                + "{\"id\":43,\"notification_type\":43,\"data\":{\"topic_title\":\"Boost主题\"}}]}";
        List<LinuxDoFeaturePayloadParser.NotificationRow> rows =
                LinuxDoFeaturePayloadParser.parseNotifications(json);
        assertEquals("reaction", rows.get(0).type);
        assertEquals("表情主题", rows.get(0).title);
        assertEquals("alice", rows.get(0).username);
        assertTrue(rows.get(0).avatar.contains("alice/96/1.png"));
        assertEquals("boost", rows.get(1).type);
        assertEquals("Boost主题", rows.get(1).title);
    }

    @Test
    public void trustProgressKeepsRequiredCurrentAndCompletion() {
        String html = "<div class='tl3-ring status-met' style='--val: 15; --max: 15'>"
                + "<span class='tl3-ring-label'>访问天数</span></div>"
                + "<div class='tl3-bar-item' style='--val: 37; --max: 100'>"
                + "<span class='tl3-bar-label'>阅读帖子</span>"
                + "<span class='tl3-bar-nums'>37 / 100</span></div>";
        List<LinuxDoFeaturePayloadParser.TrustRequirement> rows =
                LinuxDoFeaturePayloadParser.parseTrustProgress(html);
        assertEquals(2, rows.size());
        assertEquals("访问天数", rows.get(0).label);
        assertEquals(15, rows.get(0).current);
        assertEquals(15, rows.get(0).required);
        assertTrue(rows.get(0).met);
        assertEquals(37, rows.get(1).current);
        assertEquals(100, rows.get(1).required);
        assertFalse(rows.get(1).met);
    }

    @Test
    public void profileKeepsTrustBadgesAndNativeTargets() {
        String user = "{\"user\":{\"username\":\"alice\",\"trust_level\":2,"
                + "\"avatar_template\":\"/avatar/{size}.png\",\"bio_cooked\":\"<p>简介</p>\"}}";
        String summary = "{\"user_summary\":{\"likes_received\":12,"
                + "\"top_topics\":[{\"id\":8,\"title\":\"主题\",\"posts_count\":2}]}}";
        LinuxDoFeaturePayloadParser.Profile profile =
                LinuxDoFeaturePayloadParser.parseProfile(user, summary, null);
        assertEquals(2, profile.trustLevel);
        assertEquals("简介", profile.bio);
        assertEquals(8, profile.rows.get(0).topicId);
    }

    @Test
    public void profileActivityAddsRecentReplyTargetsAndKeepsStats() {
        String user = "{\"user\":{\"username\":\"alice\",\"post_count\":3}}";
        String activity = "{\"user_actions\":[{\"topic_id\":8,\"post_number\":5,"
                + "\"topic_title\":\"活动主题\",\"action_type\":\"post_created\","
                + "\"created_at\":\"2026-08-16T00:00:00Z\"}]}";
        LinuxDoFeaturePayloadParser.Profile profile =
                LinuxDoFeaturePayloadParser.parseProfile(user, null, null, activity);
        assertEquals(3, profile.postCount);
        assertEquals(1, profile.rows.size());
        assertTrue(profile.rows.get(0).isReply);
        assertEquals(5, profile.rows.get(0).postNumber);
        assertEquals(1, profile.activityOffset);
        assertFalse(profile.activityHasMore);
    }

    @Test
    public void profileActivityPageIsBoundedAndAdvertisesContinuation() {
        StringBuilder activity = new StringBuilder("{\"user_actions\":[");
        for (int i = 0; i < LinuxDoFeaturePayloadParser.PROFILE_ACTIVITY_PAGE_SIZE + 5; i++) {
            if (i > 0) activity.append(',');
            activity.append("{\"topic_id\":").append(100 + i)
                    .append(",\"post_number\":1,\"topic_title\":\"主题")
                    .append(i).append("\"}");
        }
        activity.append("]}");
        LinuxDoFeaturePayloadParser.Profile profile =
                LinuxDoFeaturePayloadParser.parseProfile(
                        "{\"user\":{\"username\":\"alice\"}}",
                        null, null, activity.toString());
        assertEquals(LinuxDoFeaturePayloadParser.PROFILE_ACTIVITY_PAGE_SIZE,
                profile.activityOffset);
        assertTrue(profile.activityHasMore);
        assertEquals(LinuxDoFeaturePayloadParser.PROFILE_ACTIVITY_PAGE_SIZE,
                profile.rows.size());
    }
}
