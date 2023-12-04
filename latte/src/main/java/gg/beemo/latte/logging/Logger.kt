package gg.beemo.latte.logging

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.lang.reflect.Modifier
import kotlin.reflect.KProperty

// From https://github.com/mrkirby153/giveaways/blob/a0e86cbeab4841781a98b6ac3b2dfce660a0e652/src/main/kotlin/com/mrkirby153/giveaways/utils/Logger.kt
fun <T : Any> unwrapCompanionClass(clazz: Class<T>): Class<*> {
    return clazz.enclosingClass?.let { enclosingClass ->
        try {
            enclosingClass.declaredFields.find { field ->
                field.name == clazz.simpleName && Modifier.isStatic(
                    field.modifiers
                ) && field.type == clazz
            }?.run { enclosingClass }
        } catch (e: SecurityException) {
            null
        }
    } ?: clazz
}

inline val Any.log: Logger
    get() = LogManager.getLogger(unwrapCompanionClass(this::class.java))

fun getLogger(clazz: Class<*>): Logger = LogManager.getLogger(clazz)

fun getLogger(name: String): Logger = LogManager.getLogger(name)

// From https://github.com/mrkirby153/bot-core/blob/f4d45e0dbff56aa4707734a48f39123381bf25fc/src/main/kotlin/com/mrkirby153/botcore/utils/Proxies.kt#L52
object Log {

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Logger {
        return LogManager.getLogger(thisRef!!::class.java)!!
    }

    operator fun invoke(name: String) = lazy {
        LogManager.getLogger(name)!!
    }

    inline operator fun <reified T> invoke() = lazy {
        LogManager.getLogger(T::class.java)!!
    }

}
