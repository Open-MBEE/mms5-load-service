package org.openmbee.flexo.mms

import io.ktor.server.application.*
import org.openmbee.flexo.mms.plugins.*
import org.openmbee.flexo.mms.plugins.configureAuthentication
import org.openmbee.flexo.mms.plugins.configureHTTP
import org.openmbee.flexo.mms.plugins.configureMonitoring
import org.openmbee.flexo.mms.plugins.configureStorage

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {
    configureHTTP()
    configureAuthentication()
    configureMonitoring()
    configureStorage()
}
