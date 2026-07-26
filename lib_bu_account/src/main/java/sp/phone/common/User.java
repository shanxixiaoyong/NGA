package sp.phone.common;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import gov.anzong.androidnga.common.base.JavaBean;

/**
 * @author Justwen
 * @date 2017/12/26
 */
@Entity(tableName = "users")
public class User implements JavaBean {

    /**
     * Legacy schema column. New writes always set this field to null; session
     * credentials live in SessionVault instead.
     */
    @ColumnInfo(name = "cid")
    public String mCid;

    /** Opaque local id used to scope sessions and account-owned app data. */
    @ColumnInfo(name = "account_id")
    public String mAccountId;

    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "uid")
    public String mUserId;

    @ColumnInfo(name = "nick_name")
    public String mNickName;

    @ColumnInfo(name = "avatar_url")
    public String mAvatarUrl;

    public User() {
    }

    @Ignore
    public User(@NonNull String userId, String nickName, String cid) {
        mUserId = userId;
        mNickName = nickName;
        mCid = cid;
    }

    @Ignore
    public User(@NonNull String userId, String nickName) {
        mUserId = userId;
        mNickName = nickName;
    }

    public String getAvatarUrl() {
        return mAvatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        mAvatarUrl = avatarUrl;
    }

    public String getUserId() {
        return mUserId;
    }

    public void setUserId(String userId) {
        mUserId = userId;
    }

    public String getCid() {
        return mCid;
    }

    public void setCid(String cid) {
        mCid = cid;
    }

    public String getAccountId() {
        return mAccountId;
    }

    public void setAccountId(String accountId) {
        mAccountId = accountId;
    }

    public String getNickName() {
        return mNickName;
    }

    public void setNickName(String nickName) {
        mNickName = nickName;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof User && mUserId.equals(((User) obj).getUserId());
    }

    @NonNull
    @Override
    public String toString() {
        return "User{" +
                "mUserId='" + mUserId + '\'' +
                ", mNickName='" + mNickName + '\'' +
                ", mAvatarUrl='" + mAvatarUrl + '\'' +
                '}';
    }

    public String toShortString() {
        return mUserId + '/' + mNickName;
    }
}
