package gg.beemo.latte.ratelimit

import com.squareup.moshi.JsonClass

object SharedRatelimitData {

    const val RATELIMIT_TOPIC = "ratelimit"

    const val KEY_REQUEST_IDENTIFY_QUOTA = "request-identify-quota"
    const val KEY_REQUEST_GLOBAL_QUOTA = "request-global-quota"

    @JsonClass(generateAdapter = true)
    class RatelimitClientData(val requestExpiresAt: Long? = null)

}
