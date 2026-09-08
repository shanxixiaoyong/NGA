package sp.phone.linuxdo;

import static org.junit.Assert.assertEquals;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import org.junit.Test;

import java.util.List;

import sp.phone.http.bean.PostReaction;

public class LinuxDoPostPayloadParserTest {

    @Test
    public void directLikeCountWinsWhenPresent() {
        JSONObject post = JSON.parseObject(
                "{\"like_count\":7,\"actions_summary\":[{\"id\":2,\"count\":9}]}");
        assertEquals(7, LinuxDoPostPayloadParser.resolveLikeCount(post));
    }

    @Test
    public void fallsBackToDiscourseLikeActionAndAcceptsStringNumbers() {
        JSONObject post = JSON.parseObject(
                "{\"actions_summary\":[{\"id\":3,\"count\":99},"
                        + "{\"post_action_type_id\":\"2\",\"count\":\"11\"}]}");
        assertEquals(11, LinuxDoPostPayloadParser.resolveLikeCount(post));
    }

    @Test
    public void malformedOrMissingLikeMetadataIsZero() {
        assertEquals(0, LinuxDoPostPayloadParser.resolveLikeCount(new JSONObject()));
        assertEquals(0, LinuxDoPostPayloadParser.resolveLikeCount(
                JSON.parseObject("{\"like_count\":-2,\"actions_summary\":\"bad\"}")));
    }

    @Test
    public void projectsMultipleEmojiReactionsAndCurrentViewerState() {
        JSONObject post = JSON.parseObject(
                "{\"reactions\":["
                        + "{\"id\":\"heart\",\"type\":\"emoji\",\"count\":4},"
                        + "{\"reaction_value\":\"clap\",\"reaction_users_count\":\"2\"},"
                        + "{\"id\":\"heart\",\"count\":9}],"
                        + "\"current_user_reaction\":{\"id\":\"clap\"}}");

        List<PostReaction> reactions = LinuxDoPostPayloadParser.resolveReactions(post);

        assertEquals(2, reactions.size());
        assertEquals("heart", reactions.get(0).getId());
        assertEquals(9, reactions.get(0).getCount());
        assertEquals("clap", reactions.get(1).getId());
        assertEquals(2, reactions.get(1).getCount());
        assertEquals("clap", LinuxDoPostPayloadParser.resolveCurrentReactionId(post));
        assertEquals("❤️ 9  👏 2 ✓", PostReaction.formatSummary(reactions));
        assertEquals("❤️ 👏 11", PostReaction.formatCompactSummary(reactions));
    }

    @Test
    public void keyedReactionObjectsAndMainLikeFallbackAreAccepted() {
        JSONObject post = JSON.parseObject(
                "{\"like_count\":3,\"reactions\":{\"heart\":3,\"rocket\":1},"
                        + "\"current_user_used_main_reaction\":true}");

        List<PostReaction> reactions = LinuxDoPostPayloadParser.resolveReactions(post);

        assertEquals(2, reactions.size());
        PostReaction heart = reactions.get(0).getId().equals("heart")
                ? reactions.get(0) : reactions.get(1);
        PostReaction rocket = reactions.get(0).getId().equals("rocket")
                ? reactions.get(0) : reactions.get(1);
        assertEquals("heart", heart.getId());
        assertEquals(3, heart.getCount());
        assertEquals(1, rocket.getCount());
        assertEquals("heart", LinuxDoPostPayloadParser.resolveCurrentReactionId(post));
        assertEquals(true, LinuxDoPostPayloadParser.isLikedByViewer(post));
    }

    @Test
    public void currentMainReactionMarksViewerAsLiked() {
        JSONObject post = JSON.parseObject(
                "{\"reactions\":[{\"id\":\"heart\",\"count\":4}],"
                        + "\"current_user_reaction\":{\"id\":\"heart\"}}");
        assertEquals(true, LinuxDoPostPayloadParser.isLikedByViewer(post));
    }

    @Test
    public void currentCustomReactionDoesNotPretendToBeCoreLike() {
        JSONObject post = JSON.parseObject(
                "{\"reactions\":[{\"id\":\"clap\",\"count\":4}],"
                        + "\"current_user_reaction\":{\"id\":\"clap\"}}");
        assertEquals(false, LinuxDoPostPayloadParser.isLikedByViewer(post));
    }

    @Test
    public void malformedReactionIdsAreSkippedBeforeMutation() {
        assertEquals(false, LinuxDoPostPayloadParser.isSafeReactionId("../post_actions"));
        assertEquals(false, LinuxDoPostPayloadParser.isSafeReactionId(""));
        assertEquals(true, LinuxDoPostPayloadParser.isSafeReactionId("confetti_ball"));
    }

    @Test
    public void reactionCountFallsBackWhenCoreActionIsZero() {
        JSONObject post = JSON.parseObject(
                "{\"like_count\":0,\"actions_summary\":[{\"id\":2,\"count\":0}],"
                        + "\"reactions\":[{\"id\":\"heart\",\"count\":5}]}");
        assertEquals(5, LinuxDoPostPayloadParser.resolveLikeCount(post));
    }

    @Test
    public void nestedKeyedHeartCountFeedsRightSideLikeScore() {
        JSONObject post = JSON.parseObject(
                "{\"like_count\":0,\"reactions\":{"
                        + "\"heart\":{\"count\":44},\"laughing\":{\"count\":34}}}");

        assertEquals(44, LinuxDoPostPayloadParser.resolveLikeCount(post));
        assertEquals(44, PostReaction.mainLikeCount(
                LinuxDoPostPayloadParser.resolveReactions(post)));
    }

    @Test
    public void unknownCustomReactionUsesGlyphInsteadOfColonShortcode() {
        PostReaction reaction = new PostReaction("partyparrot", "emoji", 3, true);

        assertEquals("🦜", PostReaction.emojiFor(reaction.getId()));
        assertEquals("🦜 3 ✓", PostReaction.formatSummary(
                java.util.Collections.singletonList(reaction)));
        assertEquals("🙂", PostReaction.emojiFor("site_custom_without_unicode"));
    }
}
