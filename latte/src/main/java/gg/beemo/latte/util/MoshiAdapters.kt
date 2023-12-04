package gg.beemo.latte.util

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import java.time.Instant

class MoshiInstantAdapter : JsonAdapter<Instant>() {

    @FromJson
    override fun fromJson(reader: JsonReader): Instant? {
        return Instant.ofEpochMilli(reader.nextLong())
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: Instant?) {
        writer.value(value?.toEpochMilli())
    }

}

class MoshiUnitAdapter : JsonAdapter<Unit>() {

    @FromJson
    override fun fromJson(reader: JsonReader): Unit? {
        reader.skipValue()
        return Unit
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: Unit?) {
        writer.nullValue()
    }

}

// If the value is outside the safe range for JavaScript numbers (which is [-(2^53)+1, (2^53)-1]),
// serialize it as a string so JavaScript clients can parse it without losing precision.
class MoshiJsLongAdapter : JsonAdapter<Long>() {

    @FromJson
    override fun fromJson(reader: JsonReader): Long? {
        return if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull<Long>()
        } else if (reader.peek() == JsonReader.Token.STRING) {
            reader.nextString().toLong()
        } else {
            reader.nextLong()
        }
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: Long?) {
        if (value == null) {
            writer.nullValue()
        } else if (value > MAX_SAFE_LONG || value < MIN_SAFE_LONG) {
            writer.value(value.toString())
        } else {
            writer.value(value)
        }
    }

    companion object {
        private const val MAX_SAFE_LONG = 9007199254740991L // 2^53-1
        private const val MIN_SAFE_LONG = -9007199254740991L // -(2^53-1)
    }

}
