package org.openmbee.mms5.plugins

import io.ktor.http.*
import io.ktor.features.*
import io.ktor.application.*
import io.ktor.response.*

abstract class HttpException(msg: String, private val statusCode: HttpStatusCode): Exception(msg) {
    open suspend fun handle(call: ApplicationCall, text: String?=this.stackTraceToString()) {
        call.respondText(text ?: "Unspecified error", status=statusCode)
    }
}

fun Application.configureHTTP() {
    install(ForwardedHeaderSupport) // WARNING: for security, do not include this if not behind a reverse proxy
    install(XForwardedHeaderSupport) // WARNING: for security, do not include this if not behind a reverse proxy
    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }

    install(StatusPages) {
        exception<HttpException> { cause ->
            cause.handle(call)
        }
        exception<Throwable> { cause ->
            call.respondText(cause.stackTraceToString(), status=HttpStatusCode.InternalServerError)
        }
    }

    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        header(HttpHeaders.ContentType)
        anyHost() // @TODO: make configuration
    }

    install(ConditionalHeaders)
}
