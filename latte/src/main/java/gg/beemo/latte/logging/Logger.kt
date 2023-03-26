package gg.beemo.latte.logging

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.lang.reflect.Modifier

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
