package com.justwen.androidnga.base.network.login

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import java.nio.charset.Charset

internal object NgaLoginResponseParser {
    private const val ASSIGNMENT_PREFIX = "window.script_muti_get_var_store="
    private val responseCharset = Charset.forName("GB18030")

    fun parse(bytes: ByteArray): NgaLoginResult {
        val decoded = bytes.toString(responseCharset).trim()
        val jsonText = when {
            decoded.startsWith(ASSIGNMENT_PREFIX) -> decoded.removePrefix(ASSIGNMENT_PREFIX).trim()
            decoded.startsWith('{') -> decoded
            else -> return malformed()
        }.removeSuffix(";").trim()

        val root = try {
            JSON.parseObject(jsonText)
        } catch (_: JSONException) {
            null
        } catch (_: RuntimeException) {
            null
        } ?: return malformed()

        extractError(root["error"])?.let { return classifyError(it) }

        val data = root["data"]
        val account = when (data) {
            is JSONArray -> data.getOrNull(3) as? JSONObject
            is JSONObject -> (data["3"] as? JSONObject) ?: data.takeIf { it.containsKey("uid") }
            else -> null
        } ?: return NgaLoginResult.Failure(
            kind = NgaLoginFailureKind.PROTOCOL_CHANGED,
            message = "登录服务返回了不支持的数据格式，请改用网页登录",
            retryable = false,
        )

        val uid = account.getString("uid")?.trim().orEmpty()
        val token = account.getString("token")?.trim().orEmpty()
        if (!NgaLoginSessionContract.isValid(uid, token)) {
            return NgaLoginResult.Failure(
                kind = NgaLoginFailureKind.PROTOCOL_CHANGED,
                message = "登录服务未返回完整会话，请改用网页登录",
                retryable = false,
            )
        }

        return NgaLoginResult.Success(
            uid = uid,
            cid = token,
            username = account.getString("username")?.trim().orEmpty(),
        )
    }

    private fun extractError(value: Any?): String? = when (value) {
        is JSONArray -> value.asSequence().mapNotNull(::safeText).firstOrNull()
        is JSONObject -> safeText(value["0"])
            ?: value.values.asSequence().mapNotNull(::safeText).firstOrNull()
        else -> safeText(value)
    }

    private fun safeText(value: Any?): String? = (value as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun classifyError(message: String): NgaLoginResult.Failure {
        val normalized = message.lowercase()
        return when {
            normalized.contains("频繁") || normalized.contains("稍后") ||
                normalized.contains("rate") || normalized.contains("limit") -> NgaLoginResult.Failure(
                NgaLoginFailureKind.RATE_LIMITED,
                "请求过于频繁，请稍后再试",
            )
            normalized.contains("验证码") || normalized.contains("captcha") -> {
                val incorrect = normalized.contains("错误") || normalized.contains("不正确") ||
                    normalized.contains("incorrect") || normalized.contains("wrong")
                NgaLoginResult.Failure(
                    if (incorrect) NgaLoginFailureKind.CAPTCHA_INCORRECT else NgaLoginFailureKind.CAPTCHA_REQUIRED,
                    if (incorrect) "验证码不正确，请重新输入" else "请输入图形验证码",
                )
            }
            normalized.contains("密码") || normalized.contains("账号") || normalized.contains("用户") ||
                normalized.contains("password") || normalized.contains("credential") -> NgaLoginResult.Failure(
                NgaLoginFailureKind.INVALID_CREDENTIALS,
                "账号或密码不正确",
            )
            else -> NgaLoginResult.Failure(
                NgaLoginFailureKind.PROTOCOL_CHANGED,
                "登录服务暂时无法处理该请求，请稍后重试或使用网页登录",
                retryable = false,
            )
        }
    }

    private fun malformed() = NgaLoginResult.Failure(
        kind = NgaLoginFailureKind.MALFORMED_RESPONSE,
        message = "无法解析登录响应，请稍后重试或使用网页登录",
        retryable = false,
    )
}

private fun JSONArray.getOrNull(index: Int): Any? = if (index in 0 until size) get(index) else null
