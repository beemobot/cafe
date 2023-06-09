package gg.beemo.latte.broker.kafka

import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record

class KafkaProcessorImpl<K, V>(
    private val cb: (r: Record<K, V>, c: ProcessorContext<Void, Void>) -> Unit
) : Processor<K, V, Void, Void> {

    private var context: ProcessorContext<Void, Void>? = null

    override fun init(context: ProcessorContext<Void, Void>) {
        this.context = context
    }

    override fun process(record: Record<K, V>) {
        context?.let { cb(record, it) }
    }

}
