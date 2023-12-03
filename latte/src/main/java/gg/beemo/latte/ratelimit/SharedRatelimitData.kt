package gg.beemo.latte.ratelimit

import com.squareup.moshi.JsonClass

object SharedRatelimitData {

    const val RATELIMIT_TOPIC = "ratelimit"

    const val KEY_REQUEST_IDENTIFY_QUOTA = "quota.identify.request"
    const val KEY_REQUEST_GLOBAL_QUOTA = "quota.global.request"

    @JsonClass(generateAdapter = true)
    class RatelimitClientData(val discordClientId: String?, val requestExpiresAt: Long? = null)

}
