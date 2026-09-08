package sp.phone.http.bean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gov.anzong.androidnga.common.base.JavaBean;

/**
 * 每一行的内容
 */
public class ThreadRowInfo implements JavaBean {

    public int tid;
    public int fid;
    public String author;//user name
    public int authorid;
    public String subject;
    public String vote;
    public String postdate;
    public int pid;
    public boolean isanonymous = false;
    public String alterinfo;// something like "edited by ..."
    public String content;
    public int lou;
    public Map<String, Attachment> attachs;
    public String level;
    public String yz; //negative integer if user is nuked
    public String js_escap_avatar;//avatar url
    public String muteTime;
    public int aurvrc;//prestige
    public String signature;
    public List<ThreadRowInfo> comments;
    public List<String> hotReplies; //热门回复

    public boolean isInBlackList;

    public String mFormattedHtmlData;

    public String from_client;
    public String from_client_model;

    public boolean mMuted;

    public String mPostCount;

    public float mReputation;

    public String mMemberGroup;

    public String ipLoc;

    public List<String> mImageUrlList = new ArrayList<>();

    public int score;

    /** Optional source-native reactions (currently populated by the LINUX DO adapter). */
    private transient List<PostReaction> mReactions = new ArrayList<>();

    /** The reaction selected by the signed-in viewer, if Discourse supplied one. */
    private transient String mCurrentReaction;

    /** Core like state supplied by an external source, used for like/undo transitions. */
    private transient boolean mLikedByViewer;

    /** Compact source-native Boost replies rendered below the floor action bar. */
    private transient List<BoostInfo> mBoosts = new ArrayList<>();

    /** Source-native Discourse polls attached to this floor. */
    private transient List<PollInfo> mPolls = new ArrayList<>();

    /** The floor this Discourse post replies to, rendered as a locally expandable context card. */
    private transient ReplyInfo mReplyTo;

    /** Direct replies already present in the current topic projection. */
    private transient List<ReplyInfo> mDirectReplies = new ArrayList<>();

    /** Authoritative reply count; the remaining rows are fetched only when the reader expands. */
    private transient int mDirectReplyCount;

    public ReplyInfo getReplyTo() {
        return mReplyTo;
    }

    public void setReplyTo(ReplyInfo replyTo) {
        mReplyTo = replyTo;
    }

    public List<ReplyInfo> getDirectReplies() {
        return mDirectReplies;
    }

    public void setDirectReplies(List<ReplyInfo> directReplies) {
        mDirectReplies = directReplies == null ? new ArrayList<>() : directReplies;
    }

    public int getDirectReplyCount() {
        return mDirectReplyCount;
    }

    public void setDirectReplyCount(int directReplyCount) {
        mDirectReplyCount = Math.max(0, directReplyCount);
    }

    public static final class ReplyInfo implements JavaBean {
        private int mPostId;
        private int mFloor;
        private String mAuthor;
        private String mContent;
        private String mAvatarUrl;

        public int getPostId() { return mPostId; }
        public void setPostId(int postId) { mPostId = postId; }
        public int getFloor() { return mFloor; }
        public void setFloor(int floor) { mFloor = Math.max(0, floor); }
        public String getAuthor() { return mAuthor; }
        public void setAuthor(String author) { mAuthor = author; }
        public String getContent() { return mContent; }
        public void setContent(String content) { mContent = content; }
        public String getAvatarUrl() { return mAvatarUrl; }
        public void setAvatarUrl(String avatarUrl) { mAvatarUrl = avatarUrl; }
    }

    public List<PollInfo> getPolls() {
        return mPolls;
    }

    public void setPolls(List<PollInfo> polls) {
        mPolls = polls == null ? new ArrayList<>() : polls;
    }

    public static final class PollInfo implements JavaBean {
        private String mName;
        private String mTitle;
        private String mType;
        private String mStatus;
        private int mMin = 1;
        private int mMax = 1;
        private int mVoters;
        private List<PollOptionInfo> mOptions = new ArrayList<>();
        private List<String> mSelectedOptionIds = new ArrayList<>();

        public String getName() { return mName; }
        public void setName(String name) { mName = name; }
        public String getTitle() { return mTitle; }
        public void setTitle(String title) { mTitle = title; }
        public String getType() { return mType; }
        public void setType(String type) { mType = type; }
        public String getStatus() { return mStatus; }
        public void setStatus(String status) { mStatus = status; }
        public int getMin() { return mMin; }
        public void setMin(int min) { mMin = Math.max(1, min); }
        public int getMax() { return mMax; }
        public void setMax(int max) { mMax = Math.max(1, max); }
        public int getVoters() { return mVoters; }
        public void setVoters(int voters) { mVoters = Math.max(0, voters); }
        public List<PollOptionInfo> getOptions() { return mOptions; }
        public void setOptions(List<PollOptionInfo> options) {
            mOptions = options == null ? new ArrayList<>() : options;
        }
        public List<String> getSelectedOptionIds() { return mSelectedOptionIds; }
        public void setSelectedOptionIds(List<String> selectedOptionIds) {
            mSelectedOptionIds = selectedOptionIds == null
                    ? new ArrayList<>() : selectedOptionIds;
        }
        public boolean isOpen() { return "open".equalsIgnoreCase(mStatus); }
        public boolean isMultiple() { return "multiple".equalsIgnoreCase(mType); }
        public boolean isRankedChoice() {
            return "ranked_choice".equalsIgnoreCase(mType);
        }
    }

    public static final class PollOptionInfo implements JavaBean {
        private String mId;
        private String mText;
        private int mVotes = -1;

        public PollOptionInfo() { }

        public PollOptionInfo(String id, String text, int votes) {
            mId = id;
            mText = text;
            mVotes = votes;
        }

        public String getId() { return mId; }
        public void setId(String id) { mId = id; }
        public String getText() { return mText; }
        public void setText(String text) { mText = text; }
        public int getVotes() { return mVotes; }
        public void setVotes(int votes) { mVotes = votes; }
    }

    public List<BoostInfo> getBoosts() {
        return mBoosts;
    }

    public void setBoosts(List<BoostInfo> boosts) {
        mBoosts = boosts == null ? new ArrayList<>() : boosts;
    }

    public static final class BoostInfo implements JavaBean {
        private String mAvatarUrl;
        private String mContent;

        public String getAvatarUrl() {
            return mAvatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            mAvatarUrl = avatarUrl;
        }

        public String getContent() {
            return mContent;
        }

        public void setContent(String content) {
            mContent = content;
        }
    }

    public void addImageUrl(String url) {
        mImageUrlList.add(url);
    }

    public List<String> getImageUrls() {
        return mImageUrlList;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public List<PostReaction> getReactions() {
        return mReactions;
    }

    public void setReactions(List<PostReaction> reactions) {
        mReactions = reactions == null ? new ArrayList<>() : reactions;
    }

    public String getCurrentReaction() {
        return mCurrentReaction;
    }

    public void setCurrentReaction(String currentReaction) {
        mCurrentReaction = currentReaction;
    }

    public boolean isLikedByViewer() {
        return mLikedByViewer;
    }

    public void setLikedByViewer(boolean likedByViewer) {
        mLikedByViewer = likedByViewer;
    }


    public void set_IsInBlackList(boolean isin) {
        this.isInBlackList = isin;
    }

    public boolean get_isInBlackList() {
        return isInBlackList;
    }

    public Map<String, Attachment> getAttachs() {
        return attachs;
    }

    public void setAttachs(Map<String, Attachment> attachs) {
        this.attachs = attachs;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getJs_escap_avatar() {
        return js_escap_avatar;
    }

    public void setJs_escap_avatar(String js_escap_avatar) {
        this.js_escap_avatar = js_escap_avatar;
    }

    public String getAlterinfo() {
        return alterinfo;
    }

    public void setAlterinfo(String alterinfo) {
        this.alterinfo = alterinfo;
    }

    public boolean getISANONYMOUS() {
        return isanonymous;
    }

    public void setISANONYMOUS(boolean isanonymous) {
        this.isanonymous = isanonymous;
    }

    public int getLou() {
        return lou;
    }

    public void setLou(int lou) {
        this.lou = lou;
    }

    public int getTid() {
        return tid;
    }

    public void setTid(int tid) {
        this.tid = tid;
    }

    public int getAuthorid() {
        return authorid;
    }

    public void setAuthorid(int authorid) {
        this.authorid = authorid;
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public int getFid() {
        return fid;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getVote() {
        return vote;
    }

    public void setVote(String vote) {
        this.vote = vote;
    }

    public String getPostdate() {
        return postdate;
    }

    public void setPostdate(String postdate) {
        this.postdate = postdate;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ThreadRowInfo> getComments() {
        return comments;
    }

    public void setComments(List<ThreadRowInfo> comments) {
        this.comments = comments;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public int getAurvrc() {
        return aurvrc;
    }

    public void setAurvrc(int aurvrc) {
        this.aurvrc = aurvrc;
    }

    public String getFromClient() {
        return from_client;
    }

    public void setFromClient(String from_client) {
        this.from_client = from_client;
    }

    public String getFromClientModel() {
        return from_client_model;
    }

    public void setFromClientModel(String from_client_model) {
        this.from_client_model = from_client_model;
    }


    public String getYz() {
        return yz;
    }

    public void setYz(String yz) {
        this.yz = yz;
    }

    public String getMuteTime() {
        return muteTime;
    }

    public void setMuteTime(String muteTime) {
        this.muteTime = muteTime;
    }

    public String getFormattedHtmlData() {
        return mFormattedHtmlData;
    }

    public void setFormattedHtmlData(String formattedHtmlData) {
        mFormattedHtmlData = formattedHtmlData;
    }

    public boolean isMuted() {
        return mMuted;
    }

    public void setMuted(boolean muted) {
        mMuted = muted;
    }

    public String getPostCount() {
        return mPostCount;
    }

    public void setPostCount(String postCount) {
        mPostCount = postCount;
    }

    public float getReputation() {
        return mReputation;
    }

    public void setReputation(float reputation) {
        mReputation = reputation;
    }

    public String getMemberGroup() {
        return mMemberGroup;
    }

    public void setMemberGroup(String memberGroup) {
        mMemberGroup = memberGroup;
    }

    public String getIpLoc() {
        return ipLoc;
    }

    public void setIpLoc(String ipLoc) {
        this.ipLoc = ipLoc;
    }
}
