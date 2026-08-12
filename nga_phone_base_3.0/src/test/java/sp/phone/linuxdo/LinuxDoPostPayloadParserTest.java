package sp.phone.linuxdo;

import static org.junit.Assert.assertEquals;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import org.junit.Test;

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
}
