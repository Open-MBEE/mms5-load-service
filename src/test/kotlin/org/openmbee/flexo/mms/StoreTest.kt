package org.openmbee.flexo.mms

import org.testcontainers.containers.GenericContainer
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.typesafe.config.ConfigFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.*
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.io.InputStreamReader
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


@Testcontainers
class StoreTest {
    companion object{
        //JWT settings
        val issuer = "https://localhost/"
        val audience = "test-audience"
        val secret = "testsecret"
        val relm = "Test Relm"

        //MINIO settings
        val MINIO_ACCESS_KEY = "admintest"      //This and MINIO_SECRET_KEY under "environment" in docker-compose.yml
        val MINIO_SECRET_KEY = "admintest"
        val MINIO_PORT_NUMBER = 9000

        var minioContainer: GenericContainer<Nothing> = GenericContainer<Nothing>("minio/minio:RELEASE.2022-05-26T05-48-41Z.hotfix.15f13935a").apply {
            val minioENVs: Map<String, String> = mapOf(
                "MINIO_PORT_NUMBER" to "${MINIO_PORT_NUMBER}",
                "MINIO_ACCESS_KEY" to MINIO_ACCESS_KEY,
                "MINIO_SECRET_KEY" to MINIO_SECRET_KEY
            )
            withExposedPorts(MINIO_PORT_NUMBER)
            withEnv(minioENVs)
            withCommand("server /tmp/data")
            waitingFor(Wait.forLogMessage(".*1 Online, 0 Offline.*", 1))
        }

        var testEnv = MapApplicationConfig(
            "jwt.audience" to audience,
            "jwt.realm" to relm,
            "jwt.domain" to issuer,
            "jwt.secret" to secret,
            //add s3 params
            "s3.region" to "us-gov-west-1",
            "s3.bucket" to "load",
            "s3.access_key" to "admintest",
            "s3.secret_key" to "admintest"
        )

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            minioContainer.start()
            testEnv.put("s3.endpoint", "http://${minioContainer.host}:${minioContainer.getMappedPort(MINIO_PORT_NUMBER)}")
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            minioContainer.stop()
        }
    }

    //Can create and delete the test file so it doesn't have to be in the github
    @Test
    fun testStore() = testApplication {
        environment {
            config = testEnv
        }
        application{
            module()
        }
        assertTrue(minioContainer.isRunning())
//        val adminAuth = AuthStruct("admin", listOf("super_admins"))
//        val authToken = authorization(adminAuth)
//
//        client.post("store/TestFile.txt") {
//            headers{
//                append(HttpHeaders.Authorization, authToken)
//            }
//            setBody(object: OutgoingContent.WriteChannelContent() {
//                override val contentType = determineContentType("TestFile.txt")
//                override val contentLength = File("TestFile.txt").length().toLong() ?: 0L
//                override suspend fun writeTo(channel: ByteWriteChannel) {
//                    File("TestFile.txt").inputStream().use {input -> channel.writeAvailable(input.readBytes())}
//                }
//            })
//        }.apply {
//            assertEquals("200 OK", this.status.toString())
//            assertNotNull(this.bodyAsText()) // @TODO: Check that this checks what I want it to - might need to go into the json
//        }
    }

//    data class AuthStruct (
//        val username: String = "",
//        val groups: List<String> = listOf("")
//    )
//
//    private fun authorization(auth: AuthStruct): String {
//        val testEnv = testEnv()
//        val jwtAudience = testEnv.config.property("jwt.audience").getString()
//        val issuer = testEnv.config.property("jwt.domain").getString()
//        val secret = testEnv.config.property("jwt.secret").getString()
//        val expires = Date(System.currentTimeMillis() + (1 * 24 * 60 * 60 * 1000))
//        return "Bearer " + JWT.create()
//            .withAudience(jwtAudience)
//            .withIssuer(issuer)
//            .withClaim("username", auth.username)
//            .withClaim("groups", auth.groups)
//            .withExpiresAt(expires)
//            .sign(Algorithm.HMAC256(secret.toString()))
//            //Have to do secret.toString() so it knows it's a string (avoid overloading ambiguity with byteArray)
//    }
//
//    fun testEnv(): ApplicationEngineEnvironment {
//        return createTestEnvironment {
//            javaClass.classLoader.getResourceAsStream("application.conf.test")?.let { it ->
//                InputStreamReader(it).use { iit ->
//                    config = HoconApplicationConfig(ConfigFactory.parseReader(iit).resolve())
//                }
//            }
//        }
//    }
//    private fun determineContentType(filePath: String): ContentType{
//        val path = FileSystems.getDefault().getPath(filePath)
//        return Files.probeContentType(path)?.let { ContentType.parse(it) } ?: ContentType.Application.OctetStream
//    }
}


//minio server
//        val minioContainer: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2022-05-26T05-48-41Z.hotfix.15f13935a").apply{
//            val MinIOEnv: Map<String, String> = mapOf(
//                "MINIO_PORT_NUMBER" to "${MINIO_PORT_NUMBER}",
//                "MINIO_ACCESS_KEY" to MINIO_ACCESS_KEY,
//                "MINIO_SECRET_KEY" to MINIO_SECRET_KEY
//            )
//            withExposedPorts(MINIO_PORT_NUMBER)
//            withEnv(MinIOEnv)
//            withCommand("server /tmp/data")
//            waitingFor(Wait.forHttp("/minio/health/live").forStatusCode(200)))
//        }