package gg.beemo.latte.ratelimit

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

object SharedRatelimitData {

    const val RATELIMIT_TOPIC = "ratelimit"

    const val KEY_REQUEST_QUOTA = "quota.request"

    @JsonClass(generateAdapter = true)
    class RatelimitRequestData(
        val type: RatelimitType,
        val discordClientId: String?,
        val requestExpiresAt: Long? = null,
    )

    enum class RatelimitType {
        @Json(name = "global")
        GLOBAL,

        @Json(name = "identify")
        IDENTIFY,
    }

}
