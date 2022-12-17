package kvant.events.kotlin

import kvant.events.event.EventObject
import kvant.events.event.EventResult
import kvant.events.handler.MethodHandler
import kvant.events.manager.EventManager
import kvant.events.model.CallResult
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.kotlinFunction

suspend fun MethodHandler.executeSuspend(): Any? {
    val method = wrappedMethod.method.kotlinFunction ?: error("Unknown function")

    return method.callSuspend(target, args)
}

suspend fun EventManager.callSuspend(event: Any, vararg args: Any): CallResult {
    val eventObj = EventObject(event)

    val results = arrayListOf<EventResult>()

    getHandlers(eventObj, args).map { it as MethodHandler }.forEach {
        val result = try {
            EventResult(eventObj, it.executeSuspend())
        } catch (e: Exception) {
            EventResult(eventObj, e)
        }

        results.add(result)
    }

    return CallResult(eventObj, results)
}