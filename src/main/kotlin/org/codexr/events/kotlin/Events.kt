package org.codexr.events.kotlin

import org.codexr.events.event.EventObject
import org.codexr.events.event.EventResult
import org.codexr.events.handler.MethodHandler
import org.codexr.events.manager.EventManager
import org.codexr.events.marker.Event
import org.codexr.events.model.CallResult
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.kotlinFunction

suspend fun MethodHandler.executeSuspend(): Any? {
    val method = wrappedMethod.method.kotlinFunction ?: error("Unknown function")

    return method.callSuspend(target, args)
}

suspend fun EventManager.callSuspend(event: Event, vararg args: Any): CallResult {
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