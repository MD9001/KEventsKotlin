package kvant.events.kotlin

import kvant.events.annotation.EventHandler
import kvant.events.event.Event
import kvant.events.event.ValueEvent
import kvant.events.exception.EventFireException
import kvant.events.kotlin.handler.KFunctionHandler
import kvant.events.listener.Listener
import kvant.events.manager.EventDispatcher
import kvant.events.manager.event.DelayedValueEvent
import kvant.events.manager.event.EventErrorEvent
import kvant.events.model.EventResult
import kvant.events.model.ScheduledEventData
import kvant.events.model.ValueList
import kvant.events.ticker.AsyncTicker
import kvant.events.ticker.Ticker
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.starProjectedType

class KEventManager: EventDispatcher {
    var throwOnFail = true
    var ticker: Ticker = AsyncTicker()

    private val listeners = ArrayList<Listener>()
    private val cache = HashMap<Event, Map<Listener, List<KFunction<*>>>>()

    private val scheduledEvents = ConcurrentHashMap<Event, ScheduledEventData>()

    suspend fun fireSuspend(event: Event, vararg args: Any) {
        getHandlers(event, args).forEach {
            try {
                it.executeSuspend()
            } catch (e: Exception) {
                if (throwOnFail())
                    throw EventFireException(e)
                else
                    fire(EventErrorEvent(event, e))
            }
        }
    }

    suspend fun <T> callSuspend(event: ValueEvent<T>, vararg args: Any?): ValueList<T> {
        val values = ValueList<T>()

        getHandlers(event, args).forEach { func ->
            try {
                val value = func.callSuspend()
                values.add(value as EventResult<T>)
            } catch (e: Exception) {
                if (throwOnFail()) {
                    throw EventFireException(e)
                }

                fire(EventErrorEvent(event, e))
            }
        }
        return values
    }

    override fun throwOnFail() = throwOnFail

    override fun registerListener(listener: Listener) {
        listeners.add(listener)
    }

    override fun scheduleEvent(event: Event, time: Instant, vararg args: Any) {
        scheduledEvents[event] = ScheduledEventData(time, args)
    }

    override fun getHandlers(event: Event, vararg args: Any): List<KFunctionHandler> {
        val functions = cache[event] ?: findFunctions(event)

        val arguments = Array<Any>(args.size + 1) {}
        arguments[0] = event

        System.arraycopy(args, 0, arguments, 1, args.size)

        println(arguments.map { it })
        val handlers = ArrayList<KFunctionHandler>()

        functions.forEach { (listener, func) ->
            handlers.addAll(
                func.filter { isApplicable(it, arguments) }
                    .filter {
                        val type = if (event is ValueEvent<*>) {
                            event.clazz.kotlin.starProjectedType
                        } else {
                            Unit::class.starProjectedType
                        }

                        it.returnType == type
                    }
                    .map { KFunctionHandler(listener, it, arguments) }
            )
        }

        return handlers
    }

    private fun isApplicable(method: KFunction<*>, args: Array<Any>): Boolean {
        val typeParams = method.parameters

        if (typeParams.size != args.size + 1) return false

        var typesValid = true

        for (i in args.indices) {
            val typeParam = typeParams[i + 1].type

            val argType = args[i]::class.starProjectedType

            if (typeParam != argType) {
                typesValid = false
                break
            }
        }
        return typesValid
    }

    private fun findFunctions(event: Event): Map<Listener, List<KFunction<*>>> {
        val map = HashMap<Listener, List<KFunction<*>>>()

        listeners.forEach {
            val classMethods = it::class.declaredFunctions
                .filter { f ->
                    f.annotations.any { a -> a.annotationClass == EventHandler::class }
                }
                .filter { f ->
                    val params = f.parameters.map { it.type }

                    params.size >= 2 && params[1] == event::class.starProjectedType
                }

            map[it] = classMethods
        }

        cache[event] = map

        return map
    }

    fun handleTicker() {
        val task = Runnable {
            scheduledEvents.forEach { (event: Event?, data: ScheduledEventData) ->
                if (Instant.now() < data.fireTime) return@forEach

                scheduledEvents.remove(event)

                if (event is ValueEvent<*>) {
                    val value: ValueList<*> = call(event, *data.args)
                    val delayedValueEvent = DelayedValueEvent(event, value.values)

                    fire(delayedValueEvent)
                } else {
                    fire(event, *data.args)
                }
            }
        }

        ticker.until { listeners.isEmpty() }
            .step(100L)
            .run(task)
    }
}