package org.openmbee.flexo.mms

import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.config.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.*
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.util.*
import kotlin.test.assertEquals


@testcontainers
class StoreTest {
    //MINIO settings
    val MINIO_ACCESS_KEY = "admintest"      //This and MINIO_SECRET_KEY under "environment"
    val MINIO_SECRET_KEY = "admintest"
    val MINIO_PORT_NUMBER = 9000

    //store-service settings
    val STORE_PORT_NUMBER = 8080            //fixme which number is it? 8080 or 8081 (8080 in dockerfile)


    //minio server
    val minioContainer: GenericContainer<Nothing> = GenericContainer<Nothing>("minio/minio:RELEASE.2022-05-26T05-48-41Z.hotfix.15f13935a").apply {
        val minioENVs: Map<String, String> = mapOf(
            "LDAP_PORT_NUMBER" to "${MINIO_PORT_NUMBER}",
            "MINIO_ACCESS_KEY" to MINIO_ACCESS_KEY,
            "MINIO_SECRET_KEY" to MINIO_SECRET_KEY
        )
        withCopyFileToContainer(/* */)    //fixme Put test file in here somewhere? - don't know, cluster.trig in github
        withExposedPorts(MINIO_PORT_NUMBER)
        withEnv(minioENVs)
        withCommand("server /tmp/data")
        //fixme change message? This just compares it to a string, don't know how to set the message
        waitingFor(Wait.forLogMessage(".*LDAP setup finished!.*\\n", 1)) // wait for ldap server to start
    }

    //store service
    val storeContainer: GenericContainer<Nothing> = GenericContainer<Nothing>("openmbee/flexo-mms-store-service:latest").apply {
        val storeENVs: Map<String, String> = mapOf(
            "STORE_PORT_NUMBER" to "${STORE_PORT_NUMBER}"
        )
        withExposedPorts(STORE_PORT_NUMBER)
        withEnv(storeENVs)
        //fixme don't know what the message being returned is, change it?
        waitingFor(Wait.forLogMessage(".*LDAP setup finished!.*\\n", 1)) // wait for ldap server to start - need to wait for minio?
    }

    fun beforeAll() {
        minioContainer.start()
        storeContainer.start()
    }

    fun afterAll() {
        minioContainer.stop()
        storeContainer.stop()
    }
}

@Test
fun testStore() = testApplication {
    application {
        module()
    }

    client.post("store/TestFile.txt"){
        headers {//fixme Probably needs different headers
            append(HttpHeaders.Authorization, "Basic $authBase64")
        }
    }.apply {
        assertEquals("200 OK", this.status.toString())

        //fixme Not sure that I can check this - would get a link where it's supposed to be stored? Don't know what to compare it to
        val token = Json.parseToJsonElement(this.bodyAsText()).jsonObject["token"]
            .toString()
            .removeSurrounding("\"")
    }

}