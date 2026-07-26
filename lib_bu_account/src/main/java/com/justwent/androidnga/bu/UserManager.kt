package com.justwent.androidnga.bu

import android.content.Context
import android.webkit.CookieManager
import androidx.lifecycle.MutableLiveData
import gov.anzong.androidnga.base.util.ContextUtils
import gov.anzong.androidnga.base.util.PreferenceUtils
import gov.anzong.androidnga.base.utils.ThreadProvider
import gov.anzong.androidnga.common.PreferenceKey
import gov.anzong.androidnga.db.AppDatabase
import com.justwent.androidnga.bu.session.AccountSessionSnapshot
import com.justwent.androidnga.bu.session.SessionVault
import sp.phone.common.User
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.UUID

/**
 * Account metadata and active-account coordination.
 *
 * <p>User ids/nicknames remain ordinary metadata.  The NGA CID is kept only in
 * [SessionVault]; the Room entity is deliberately scrubbed before every write
 * so legacy installations cannot keep adding plaintext credentials.</p>
 */
object UserManager {

    const val ANONYMOUS_ACCOUNT_ID = AccountSessionSnapshot.ANONYMOUS_ACCOUNT_ID

    private val accountStateLock = Any()
    private val activeIndexLiveData: MutableLiveData<Int> = MutableLiveData(0)
    private val userListLiveData: MutableLiveData<List<User>> = MutableLiveData(emptyList())

    @Volatile
    private var initialized = false
    private var activeUser: User? = null
    private var sessionGeneration = 0L

    /** Must be called after [AppDatabase.init]. The query itself runs off the UI thread. */
    fun initialize(context: Context) {
        synchronized(accountStateLock) {
            if (initialized) {
                return
            }
            val database = AppDatabase.getInstance() ?: return
            SessionVault.initialize(context)
            val users = loadAndMigrateUsers(database)
            var index = PreferenceUtils.getData(PreferenceKey.USER_ACTIVE_INDEX, 0)
            if (index !in users.indices) {
                index = 0
            }
            userListLiveData.value = users
            activeIndexLiveData.value = index
            activeUser = users.getOrNull(index)
            initialized = true
        }
    }

    private fun ensureInitialized() {
        if (!initialized) {
            ContextUtils.getApplication()?.let { initialize(it) }
        }
    }

    private fun loadAndMigrateUsers(database: AppDatabase): List<User> {
        val task = FutureTask<List<User>>(Callable {
            val users = (database.userDao().loadUser() ?: emptyList()).toMutableList()
            var changed = false
            users.forEach { user ->
                if (user.accountId.isNullOrEmpty()) {
                    user.accountId = UUID.randomUUID().toString()
                    changed = true
                }
                // Migrate the old Room cid column once. If encryption is unavailable,
                // discard the credential rather than retaining plaintext.
                val legacyCid = user.cid
                if (!legacyCid.isNullOrEmpty()) {
                    SessionVault.put(user.accountId, legacyCid)
                    user.cid = null
                    changed = true
                } else {
                    user.cid = null
                }
            }
            if (changed && users.isNotEmpty()) {
                database.userDao().updateUsers(*users.toTypedArray())
            }
            users
        })
        Thread(task, "nga-account-db-init").start()
        return try {
            task.get()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasValidUser(): Boolean {
        ensureInitialized()
        return synchronized(accountStateLock) {
            activeUser != null && userListLiveData.value?.isNotEmpty() == true
        }
    }

    fun getActiveIndex(): Int {
        ensureInitialized()
        return synchronized(accountStateLock) { getActiveIndexLocked() }
    }

    fun getActiveIndexLiveData(): MutableLiveData<Int> {
        ensureInitialized()
        return activeIndexLiveData
    }

    fun getActiveUser(): User? {
        ensureInitialized()
        return synchronized(accountStateLock) { activeUser }
    }

    fun setActiveIndex(index: Int) {
        ensureInitialized()
        synchronized(accountStateLock) {
            val users = userListLiveData.value.orEmpty()
            if (index !in users.indices) {
                return
            }
            val nextUser = users[index]
            if (activeUser?.accountId != nextUser.accountId) {
                advanceSessionGenerationLocked()
            }
            activeIndexLiveData.value = index
            activeUser = nextUser
            PreferenceUtils.putData(PreferenceKey.USER_ACTIVE_INDEX, index)
        }
    }

    fun toggleUser(isNext: Boolean): Int {
        val activeUserIndex = getNextActiveIndex(isNext)
        if (activeUserIndex >= 0) {
            setActiveIndex(activeUserIndex)
        }
        return activeUserIndex
    }

    fun getNextActiveIndex(isNext: Boolean): Int {
        ensureInitialized()
        return synchronized(accountStateLock) {
            val size = userListLiveData.value?.size ?: 0
            if (size == 0) {
                return -1
            }
            val activeIndex = getActiveIndexLocked().coerceIn(0, size - 1)
            val index = if (isNext) activeIndex + 1 else activeIndex + size - 1
            index % size
        }
    }

    fun addUser(user: User): Boolean {
        ensureInitialized()
        synchronized(accountStateLock) {
            if (user.userId.isEmpty()) {
                return false
            }
            val currentUsers = userListLiveData.value.orEmpty()
            val existingIndex = currentUsers.indexOfFirst { it.userId == user.userId }
            val existing = currentUsers.getOrNull(existingIndex)
            if (user.accountId.isNullOrEmpty()) {
                user.accountId = existing?.accountId ?: UUID.randomUUID().toString()
            }
            val credential = user.cid
            val credentialReplaced = !credential.isNullOrEmpty()
            if (credentialReplaced && !SessionVault.put(user.accountId, credential)) {
                user.cid = null
                return false
            }
            // Never allow the entity object to carry a value that Room could persist.
            user.cid = null
            val userList = currentUsers.toMutableList()
            if (existingIndex < 0) {
                userList.add(user)
            } else {
                userList[existingIndex] = user
            }
            userListLiveData.value = userList
            if (activeUser == null && userList.isNotEmpty()) {
                activeUser = userList.first()
                activeIndexLiveData.value = 0
                PreferenceUtils.putData(PreferenceKey.USER_ACTIVE_INDEX, 0)
            } else {
                activeUser = userList.getOrNull(getActiveIndexLocked())
            }
            if (credentialReplaced) {
                advanceSessionGenerationLocked()
            }
            saveUsersLocked(userList)
            return true
        }
    }

    fun addUser(uid: String, cid: String, name: String): Boolean {
        return addUser(User(uid, name, cid))
    }

    fun removeUser(index: Int) {
        ensureInitialized()
        val removed = synchronized(accountStateLock) {
            val userList = userListLiveData.value.orEmpty().toMutableList()
            if (index !in userList.indices) {
                return
            }
            val removed = userList.removeAt(index)
            SessionVault.remove(removed.accountId)
            advanceSessionGenerationLocked()
            var activeIndex = getActiveIndexLocked()
            if (activeIndex >= index) {
                activeIndex -= 1
            }
            if (userList.isEmpty()) {
                activeIndex = 0
                activeUser = null
            } else {
                activeIndex = activeIndex.coerceIn(0, userList.size - 1)
                activeUser = userList[activeIndex]
            }
            activeIndexLiveData.value = activeIndex
            PreferenceUtils.putData(PreferenceKey.USER_ACTIVE_INDEX, activeIndex)
            userListLiveData.value = userList
            removed
        }
        // WebView has one process-global jar, so logout clears it rather than
        // risking a removed account leaking into a later login flow.
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        ThreadProvider.runOnSingleThread {
            AppDatabase.getInstance()?.userDao()?.removeUsers(removed)
        }
    }

    fun getUserListLiveData(): MutableLiveData<List<User>> {
        ensureInitialized()
        return userListLiveData
    }

    fun getUserList(): List<User> {
        ensureInitialized()
        return synchronized(accountStateLock) { userListLiveData.value.orEmpty().toList() }
    }

    fun getCid(user: User?): String {
        ensureInitialized()
        return synchronized(accountStateLock) {
            user?.accountId?.let { SessionVault.get(it).orEmpty() }.orEmpty()
        }
    }

    fun getActiveAccountId(): String? {
        ensureInitialized()
        return synchronized(accountStateLock) { activeUser?.accountId }
    }

    fun getCookie(user: User? = activeUser): String {
        ensureInitialized()
        return synchronized(accountStateLock) {
            cookieForUserLocked(user)
        }
    }

    /** Captures account selection and credential material as one immutable value. */
    fun captureActiveSession(): AccountSessionSnapshot {
        ensureInitialized()
        return synchronized(accountStateLock) { captureActiveSessionLocked() }
    }

    /** Returns false after account switch, credential replacement, logout, or revocation. */
    fun isSessionCurrent(snapshot: AccountSessionSnapshot): Boolean {
        ensureInitialized()
        return synchronized(accountStateLock) {
            if (snapshot.sessionGeneration != sessionGeneration) {
                return@synchronized false
            }
            val current = captureActiveSessionLocked()
            current.accountId == snapshot.accountId &&
                current.uid == snapshot.uid &&
                current.cookieHeader == snapshot.cookieHeader &&
                current.isAnonymous == snapshot.isAnonymous
        }
    }

    /** Invalidates one still-current snapshot without deleting the saved login. */
    fun revokeSession(snapshot: AccountSessionSnapshot): Boolean {
        ensureInitialized()
        return synchronized(accountStateLock) {
            if (!isSessionCurrentLocked(snapshot)) {
                return@synchronized false
            }
            advanceSessionGenerationLocked()
            true
        }
    }

    fun setAvatarUrl(uid: String, url: String) {
        ensureInitialized()
        synchronized(accountStateLock) {
            userListLiveData.value.orEmpty().forEach { user ->
                if (user.userId == uid) {
                    if (user.avatarUrl != url) {
                        user.avatarUrl = url
                        val copy = sanitizedCopy(user)
                        ThreadProvider.runOnSingleThread {
                            AppDatabase.getInstance()?.userDao()?.updateUsers(copy)
                        }
                    }
                    return
                }
            }
        }
    }

    private fun saveUsersLocked(users: List<User>) {
        val snapshot = users.map { sanitizedCopy(it) }
        ThreadProvider.runOnSingleThread {
            val database = AppDatabase.getInstance() ?: return@runOnSingleThread
            if (snapshot.isNotEmpty()) {
                database.userDao().updateUsers(*snapshot.toTypedArray())
            }
        }
    }

    private fun sanitizedCopy(user: User): User {
        user.cid = null
        val copy = User(user.userId, user.nickName)
        copy.avatarUrl = user.avatarUrl
        copy.accountId = user.accountId
        copy.cid = null
        return copy
    }

    private fun getActiveIndexLocked(): Int = activeIndexLiveData.value ?: 0

    private fun cookieForUserLocked(user: User?): String {
        val account = user ?: return ""
        val accountId = account.accountId ?: return ""
        val cid = SessionVault.get(accountId).orEmpty()
        return if (cid.isNotEmpty() && account.userId.isNotEmpty()) {
            "ngaPassportUid=${account.userId}; ngaPassportCid=$cid"
        } else {
            ""
        }
    }

    private fun captureActiveSessionLocked(): AccountSessionSnapshot {
        val account = activeUser ?: return AccountSessionSnapshot.anonymous(sessionGeneration)
        val accountId = account.accountId
        val cookie = cookieForUserLocked(account)
        return if (!accountId.isNullOrEmpty() && account.userId.isNotEmpty() && cookie.isNotEmpty()) {
            AccountSessionSnapshot.authenticated(
                accountId,
                sessionGeneration,
                account.userId,
                cookie
            )
        } else {
            AccountSessionSnapshot.anonymous(sessionGeneration)
        }
    }

    private fun isSessionCurrentLocked(snapshot: AccountSessionSnapshot): Boolean {
        if (snapshot.sessionGeneration != sessionGeneration) {
            return false
        }
        val current = captureActiveSessionLocked()
        return current.accountId == snapshot.accountId &&
            current.uid == snapshot.uid &&
            current.cookieHeader == snapshot.cookieHeader &&
            current.isAnonymous == snapshot.isAnonymous
    }

    private fun advanceSessionGenerationLocked() {
        sessionGeneration = Math.incrementExact(sessionGeneration)
    }
}
