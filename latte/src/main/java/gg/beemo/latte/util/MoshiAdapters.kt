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
