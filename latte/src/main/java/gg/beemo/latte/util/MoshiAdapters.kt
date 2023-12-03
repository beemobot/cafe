package gg.beemo.latte.util

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.time.Instant

class MoshiInstantAdapter : JsonAdapter<Instant>() {

    override fun fromJson(reader: JsonReader): Instant? {
        return Instant.ofEpochMilli(reader.nextLong())
    }

    override fun toJson(writer: JsonWriter, value: Instant?) {
        writer.value(value?.toEpochMilli())
    }

}
