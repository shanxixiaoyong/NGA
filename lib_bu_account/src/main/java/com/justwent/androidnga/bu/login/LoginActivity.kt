package com.justwent.androidnga.bu.login

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.alibaba.android.arouter.facade.annotation.Route
import com.justwen.androidnga.base.activity.ARouterConstants
import com.justwen.androidnga.base.network.login.NgaLoginAccountType
import com.justwen.androidnga.base.network.login.NgaLoginSessionContract
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import com.justwen.androidnga.ui.compose.R as ComposeUiR
import com.justwen.androidnga.ui.compose.widget.OptionMenuData
import com.justwent.androidnga.bu.UserManager

@Route(path = ARouterConstants.ACTIVITY_LOGIN)
class LoginActivity : BaseComposeActivity() {
    private val viewModel: LoginViewModel by lazy {
        ViewModelProvider(this)[LoginViewModel::class.java]
    }
    private val webLoginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val uid = data.getStringExtra(WebLoginActivity.EXTRA_UID).orEmpty()
        val cid = data.getStringExtra(WebLoginActivity.EXTRA_CID).orEmpty()
        val username = data.getStringExtra(WebLoginActivity.EXTRA_USERNAME).orEmpty()
        if (uid.isNotBlank() && cid.isNotBlank()) completeLogin(uid, cid, username)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "登录"
    }

    override fun onStop() {
        viewModel.onHostStopped()
        super.onStop()
    }

    override fun getOptionMenuData(): List<OptionMenuData> = listOf(
        OptionMenuData(
            title = "网页登录",
            icon = ComposeUiR.drawable.btn_ic_browser,
            type = OptionMenuData.OPTION_MENU_TYPE_ALWAYS_SHOW,
            action = { webLoginLauncher.launch(Intent(this, WebLoginActivity::class.java)) },
        ),
    )

    @Composable
    override fun ContentView() {
        val state = viewModel.uiState
        var password by remember { mutableStateOf("") }
        var captcha by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var accountTypeExpanded by remember { mutableStateOf(false) }

        state.success?.let { success ->
            LaunchedEffect(success.uid, success.cid) {
                password = ""
                captcha = ""
                viewModel.consumeSuccess()
                completeLogin(success.uid, success.cid, success.username)
            }
        }
        LaunchedEffect(state.captchaImage) {
            if (state.phase == LoginPhase.CAPTCHA_REQUIRED) captcha = ""
        }
        LaunchedEffect(state.clearSensitiveSignal) {
            password = ""
            captcha = ""
            passwordVisible = false
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = accountTypeLabel(state.accountType),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("账号类型") },
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                )
                Spacer(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(enabled = state.phase != LoginPhase.SUBMITTING) {
                            accountTypeExpanded = true
                        },
                )
                DropdownMenu(
                    expanded = accountTypeExpanded,
                    onDismissRequest = { accountTypeExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f),
                ) {
                    NgaLoginAccountType.values().forEach { type ->
                        DropdownMenuItem(onClick = {
                            viewModel.updateAccountType(type)
                            accountTypeExpanded = false
                        }) {
                            Text(accountTypeLabel(type))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.account,
                onValueChange = viewModel::updateAccount,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.phase != LoginPhase.SUBMITTING,
                singleLine = true,
                label = { Text("账号") },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType(state.accountType)),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.phase != LoginPhase.SUBMITTING,
                singleLine = true,
                label = { Text("密码") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painterResource(android.R.drawable.ic_menu_view),
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
            )

            if (state.phase == LoginPhase.CAPTCHA_REQUIRED || state.captchaImage != null) {
                CaptchaSection(
                    imageBytes = state.captchaImage,
                    value = captcha,
                    enabled = state.phase != LoginPhase.SUBMITTING,
                    onValueChange = { captcha = it },
                    onRefresh = viewModel::refreshCaptcha,
                )
            }

            Text(
                text = state.errorMessage.orEmpty(),
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
            )

            Button(
                onClick = { viewModel.submit(password, captcha.takeIf { state.phase == LoginPhase.CAPTCHA_REQUIRED }) },
                enabled = state.phase != LoginPhase.SUBMITTING,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.phase == LoginPhase.SUBMITTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colors.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("登录")
                }
            }
        }
    }

    private fun completeLogin(uid: String, cid: String, username: String) {
        if (!NgaLoginSessionContract.isValid(uid, cid)) return
        UserManager.addUserAndSelect(uid, cid, username.ifBlank { uid })
        setResult(RESULT_OK)
        finish()
    }
}

@Composable
private fun CaptchaSection(
    imageBytes: ByteArray?,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val bitmap = remember(imageBytes) {
        imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "图形验证码",
                modifier = Modifier.weight(1f).height(64.dp),
            )
        } else {
            Text("验证码图片不可用", modifier = Modifier.weight(1f))
        }
        IconButton(onClick = onRefresh, enabled = enabled) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新验证码")
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text("验证码") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
}

private fun accountTypeLabel(type: NgaLoginAccountType): String = when (type) {
    NgaLoginAccountType.USERNAME -> "用户名或昵称"
    NgaLoginAccountType.EMAIL -> "邮箱"
    NgaLoginAccountType.USER_ID -> "用户 ID"
    NgaLoginAccountType.PHONE -> "手机号"
}

private fun keyboardType(type: NgaLoginAccountType): KeyboardType = when (type) {
    NgaLoginAccountType.EMAIL -> KeyboardType.Email
    NgaLoginAccountType.USER_ID -> KeyboardType.Number
    NgaLoginAccountType.PHONE -> KeyboardType.Phone
    NgaLoginAccountType.USERNAME -> KeyboardType.Text
}
