package org.openmbee.flexo.mms

import io.ktor.server.application.*
import org.openmbee.flexo.mms.plugins.*

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {
    configureHTTP()
    //configureAuthentication() //uncomment if auth is desired
    configureMonitoring()
    configureStorage()
}
