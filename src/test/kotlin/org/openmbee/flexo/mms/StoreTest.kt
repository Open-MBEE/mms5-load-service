package org.openmbee.flexo.mms

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
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.*
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.io.InputStreamReader
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.nio.file.FileSystems
import java.nio.file.Files


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

        //minio server
        val minioContainer: GenericContainer<Nothing> = GenericContainer<Nothing>("minio/minio:RELEASE.2022-05-26T05-48-41Z.hotfix.15f13935a").apply {
            val minioENVs: Map<String, String> = mapOf(
                "MINIO_PORT_NUMBER" to "${MINIO_PORT_NUMBER}",
                "MINIO_ACCESS_KEY" to MINIO_ACCESS_KEY,
                "MINIO_SECRET_KEY" to MINIO_SECRET_KEY
            )
            withExposedPorts(MINIO_PORT_NUMBER)
            withEnv(minioENVs)
            withCommand("server /tmp/data")
            // @TODO change message - look during setup to see what the last message is
            //waitingFor(Wait.forLogMessage(".*LDAP setup finished!.*\\n", 1)) // wait for ldap server to start
        }

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            minioContainer.start()
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            minioContainer.stop()
        }
    }

    @Test
    fun testStore() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.audience" to audience,
                "jwt.realm" to relm,
                "jwt.domain" to issuer,
                "jwt.secret" to secret
            )
        }
        application{
            module()
        }
        val adminAuth = AuthStruct("admin", listOf("super_admins"))
        val authToken = authorization(adminAuth)

        client.post("store/TestFile.txt") {
            headers{
                append(HttpHeaders.Authorization, authToken)
            }
            setBody(object: OutgoingContent.WriteChannelContent() {
                override val contentType = determineContentType("TestFile.txt")
                override val contentLength = File("TestFile.txt").length().toLong() ?: 0L
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    File("TestFile.txt").inputStream().use {input -> channel.writeAvailable(input.readBytes())}
                }
            })
        }.apply {
            assertEquals("200 OK", this.status.toString())
            assertNotNull(this.bodyAsText()) // @TODO: Check that this checks what I want it to - might need to go into the json
        }
    }

    data class AuthStruct (
        val username: String = "",
        val groups: List<String> = listOf("")
    )

    private fun authorization(auth: AuthStruct): String {
        val testEnv = testEnv()
        val jwtAudience = testEnv.config.property("jwt.audience").getString()
        val issuer = testEnv.config.property("jwt.domain").getString()
        val secret = testEnv.config.property("jwt.secret").getString()
        val expires = Date(System.currentTimeMillis() + (1 * 24 * 60 * 60 * 1000))
        return "Bearer " + JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(issuer)
            .withClaim("username", auth.username)
            .withClaim("groups", auth.groups)
            .withExpiresAt(expires)
            .sign(Algorithm.HMAC256(secret.toString()))
            //Have to do secret.toString() so it knows it's a string (avoid overloading ambiguity with byteArray)
    }

    fun testEnv(): ApplicationEngineEnvironment {
        return createTestEnvironment {
            javaClass.classLoader.getResourceAsStream("application.conf.test")?.let { it ->
                InputStreamReader(it).use { iit ->
                    config = HoconApplicationConfig(ConfigFactory.parseReader(iit).resolve())
                }
            }
        }
    }
    private fun determineContentType(filePath: String): ContentType{
        val path = FileSystems.getDefault().getPath(filePath)
        return Files.probeContentType(path)?.let { ContentType.parse(it) } ?: ContentType.Application.OctetStream
    }
}
