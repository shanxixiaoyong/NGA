package com.justwent.androidnga.bu.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.alibaba.android.arouter.facade.annotation.Route
import com.justwen.androidnga.base.activity.ARouterConstants
import com.justwen.androidnga.module.account.R
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import com.justwen.androidnga.ui.compose.R as ComposeUiR
import com.justwen.androidnga.ui.compose.widget.OptionMenuData
import com.justwent.androidnga.bu.UserManager
import sp.phone.common.User

@Route(path = ARouterConstants.ACTIVITY_LOGIN)
class LoginActivity : BaseComposeActivity() {
    private val webLoginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        completeLogin(
            uid = data.getStringExtra(WebLoginActivity.EXTRA_UID).orEmpty(),
            cid = data.getStringExtra(WebLoginActivity.EXTRA_CID).orEmpty(),
            username = data.getStringExtra(WebLoginActivity.EXTRA_USERNAME).orEmpty(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "登录账号"
    }

    override fun getOptionMenuData(): List<OptionMenuData> = listOf(
        OptionMenuData(
            title = "网页登录",
            icon = ComposeUiR.drawable.btn_ic_browser,
            type = OptionMenuData.OPTION_MENU_TYPE_ALWAYS_SHOW,
            action = ::launchWebLogin,
        ),
    )

    @Composable
    override fun ContentView() {
        val users by UserManager.getUserListLiveData().observeAsState(emptyList())
        val activeIndex by UserManager.getActiveIndexLiveData().observeAsState(0)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item(key = "new-account") {
                NewAccountItem(onClick = ::launchWebLogin)
            }
            itemsIndexed(
                items = users,
                key = { _, user -> user.userId },
            ) { index, user ->
                AccountItem(
                    user = user,
                    selected = index == activeIndex,
                    onSelect = { selectExistingAccount(user.userId) },
                )
            }
        }
    }

    private fun launchWebLogin() {
        webLoginLauncher.launch(Intent(this, WebLoginActivity::class.java))
    }

    private fun selectExistingAccount(userId: String) {
        val index = findAccountIndex(UserManager.getUserList(), userId)
        if (index < 0) return
        UserManager.setActiveIndex(index)
        setResult(RESULT_OK)
        finish()
    }

    private fun completeLogin(uid: String, cid: String, username: String) {
        if (!WebLoginPolicy.isValidSession(uid, cid)) return
        UserManager.addUserAndSelect(uid, cid, username.ifBlank { uid })
        setResult(RESULT_OK)
        finish()
    }
}

internal fun findAccountIndex(users: List<User>, userId: String): Int =
    users.indexOfFirst { it.userId == userId }

@Composable
private fun NewAccountItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Text(text = "登录新账号")
    }
}

@Composable
private fun AccountItem(
    user: User,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val displayName = user.nickName?.takeIf(String::isNotBlank) ?: user.userId
    val avatarPainter: Painter = rememberAsyncImagePainter(
        model = user.avatarUrl.orEmpty(),
        error = painterResource(id = R.drawable.drawerdefaulticon),
        placeholder = painterResource(id = R.drawable.drawerdefaulticon),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.size(48.dp),
        )
        Image(
            painter = avatarPainter,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Text(
            text = displayName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
