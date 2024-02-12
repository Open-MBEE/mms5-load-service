package org.openmbee.flexo.mms

import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import java.time.Duration

class ApplicationTest {
    companion object {
        //JWT settings
        val issuer = "https://localhost/"
        val audience = "test-audience"
        val secret = "testsecret"
        val relm = "Test Relm"

        //MINIO settings
        val MINIO_ACCESS_KEY = "admintest"      //This and MINIO_SECRET_KEY under "environment" in docker-compose.yml
        val MINIO_SECRET_KEY = "admintest"
        val MINIO_PORT_NUMBER = 9000


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

        val minioContainer: GenericContainer<Nothing> = GenericContainer<Nothing>("minio/minio:RELEASE.2022-05-26T05-48-41Z.hotfix.15f13935a").apply {
            val minioENVs: Map<String, String> = mapOf(
                "MINIO_PORT_NUMBER" to "${MINIO_PORT_NUMBER}",
                "MINIO_ACCESS_KEY" to MINIO_ACCESS_KEY,
                "MINIO_SECRET_KEY" to MINIO_SECRET_KEY
            )
            withExposedPorts(MINIO_PORT_NUMBER)
            withEnv(minioENVs)
            withCommand("server /tmp/data")
            waitingFor(
                HttpWaitStrategy()
                    .forPath("/minio/health/ready")
                    .forPort(MINIO_PORT_NUMBER)
                    .withStartupTimeout(Duration.ofSeconds(10))
            )
        }

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            minioContainer.start()
            testEnv.put(
                "s3.endpoint", "http://${minioContainer.host}:${minioContainer.getMappedPort(MINIO_PORT_NUMBER)}"
            )
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            minioContainer.stop()
        }
    }

    @Test
    fun testRoot() = testApplication {
        environment {
            config = testEnv
        }
        application {
            module()
        }

        Assertions.assertTrue(minioContainer.isRunning)
    }
}

//From Blake
//    @Test
//    fun testRoot() {
//        withTestApplication({ configureRouting() }) {
//            handleRequest(HttpMethod.Get, "/").apply {
//                assertEquals(HttpStatusCode.OK, response.status())
//                assertEquals("Hello World!", response.content)
//            }
//        }
//    }
//}