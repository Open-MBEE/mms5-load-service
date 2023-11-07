package org.openmbee.flexo.mms

import com.fasterxml.jackson.databind.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
//import io.ktor.features.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import org.openmbee.flexo.mms.plugins.*
import org.slf4j.event.*
import kotlin.test.*

class ApplicationTest {
    @Test
    fun testRoot() {
//        withTestApplication({ configureRouting() }) {
//            handleRequest(HttpMethod.Get, "/").apply {
//                assertEquals(HttpStatusCode.OK, response.status())
//                assertEquals("Hello World!", response.content)
//            }
//        }
    }
}