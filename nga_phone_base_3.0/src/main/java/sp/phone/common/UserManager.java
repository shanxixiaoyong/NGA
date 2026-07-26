package sp.phone.common;

import android.content.Context;

import java.util.List;

import com.justwent.androidnga.bu.session.AccountSessionSnapshot;

public interface UserManager {

    int getUserSize();

    User getActiveUser();

    void initialize(Context context);

    int getActiveUserIndex();

    List<User> getUserList();

    boolean hasValidUser();

    void setActiveUser(int index);

    int toggleUser(boolean isNext);

    void addUser(User user);

    void addUser(String uid, String cid, String name);

    void removeUser(int index);

    void swapUser(int from, int to);

    // User 类辅助接口

    String getCookie();

    String getCookie(User user);

    String getUserId();

    /** Opaque local account id used for request and local-data ownership. */
    String getActiveAccountId();

    /** Atomically captures the active account and its current session material. */
    AccountSessionSnapshot captureActiveSession();

    boolean isSessionCurrent(AccountSessionSnapshot snapshot);

    boolean revokeSession(AccountSessionSnapshot snapshot);

    String getCid();

    String getUserName();

    void setAvatarUrl(int userId, String url);

    // 黑名单

    void addToBlackList(String authorName, String authorId);

    void removeFromBlackList(String authorId);

    boolean checkBlackList(String authorId);

    String getAvatarUrl(String uid);

    void clearAvatarUrl();

}
