package org.openmbee.flexo.mms.lib

import io.ktor.http.ContentType

object MimeTypes {
    object Text {
        object TTL {
            const val extension = "ttl"
            val contentType = ContentType("text", "turtle")
        }
    }
}