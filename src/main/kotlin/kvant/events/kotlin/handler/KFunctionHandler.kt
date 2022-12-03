package kvant.events.kotlin.handler

import kvant.events.handler.Handler
import kvant.events.model.EventResult
import java.lang.Exception
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

class KFunctionHandler(private val target: Any, private val function: KFunction<*>, private val args: Array<Any>): Handler {
    override fun execute() {
        function.call(target, *args)
    }

    override fun executeForValue(): EventResult<*> {
        return try {
            val value = function.call(target, *args)

            EventResult(value, null)
        } catch (e: Exception) {
            EventResult(null, e)
        }
    }

    suspend fun executeSuspend() {
        function.callSuspend(target, *args)
    }

    suspend fun callSuspend(): EventResult<*> {
        return try {
            val value = function.callSuspend(target, *args)

            EventResult(value, null)
        } catch (e: Exception) {
            EventResult(null, e)
        }
    }
}