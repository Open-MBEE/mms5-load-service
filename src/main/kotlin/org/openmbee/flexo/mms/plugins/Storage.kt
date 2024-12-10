package org.openmbee.flexo.mms.plugins

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.plugins.autohead.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openmbee.flexo.mms.lib.MimeTypes
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.util.*

fun Application.configureStorage() {
    install(AutoHeadResponse)

    val s3Storage = S3Storage(getS3ConfigValues(environment.config))

    routing {
        //authenticate { // if authen is desired then uncomment this
            post("store/{filename}") {
                // https://www.baeldung.com/kotlin/io-and-default-dispatcher
                // s3 client file operation is blocking, on netty this will error without withContext
                // this is here to keep compatibility with layer1's load model until layer1 is updated
                withContext(Dispatchers.IO) {
                    val location = S3Storage.buildLocation(call.parameters["filename"]!!, MimeTypes.Text.TTL.extension)
                    s3Storage.store(
                        call.receiveStream(),
                        location,
                        call.request.header(HttpHeaders.ContentLength)!!.toLong(),
                        call.request.contentType()
                    )
                    call.application.log.info("Location:\n$location")
                    call.respond(s3Storage.getPreSignedUrl(location))
                }
            }
            put("store/{path...}") {
                withContext(Dispatchers.IO) {
                    val path = call.parameters.getAll("path")?.joinToString("/")
                    try {
                        s3Storage.store(
                            call.receiveStream(),
                            path!!,
                            call.request.header(HttpHeaders.ContentLength)!!.toLong(),
                            call.request.contentType()
                        )
                        call.respond(s3Storage.getPreSignedUrl(path!!))
                    } catch (e: AmazonS3Exception) {
                        call.respond(HttpStatusCode(e.statusCode, e.errorCode!!), e.errorMessage)
                    }
                }
            }

            get("signed/{path...}") {
                val path = call.parameters.getAll("path")?.joinToString("/")
                call.application.log.info("Path:\n$path")
                call.respond(s3Storage.getPreSignedUrl(path!!))
            }

            get("store/{path...}") {
                withContext(Dispatchers.IO) {
                    val path = call.parameters.getAll("path")?.joinToString("/")
                    try {
                        call.respond(s3Storage.get(path!!))
                    } catch (e: AmazonS3Exception) {
                        call.respond(HttpStatusCode(e.statusCode, e.errorCode!!), e.errorMessage)
                    }
                }
            }
        //}
    }
}

class S3Storage(s3Config: S3Config) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val s3Client = getClient(s3Config)
    private val bucket = s3Config.bucket

    fun get(location: String?): ByteArray {
        val rangeObjectRequest = GetObjectRequest(bucket, location)
        return s3Client.getObject(rangeObjectRequest).objectContent.readAllBytes()
    }

    fun getPreSignedUrl(location: String): String {
        return s3Client
            .generatePresignedUrl(bucket, location, Date.from(Instant.now().plusSeconds(30 * 60)))
            .toString()
    }

    fun store(data: InputStream, path: String, contentLength: Long, contentType: ContentType): String {
        val om = ObjectMetadata()
        om.contentType = contentType.toString()
        om.contentLength = contentLength
        val por = PutObjectRequest(bucket, path, data, om)
        try {
            s3Client.putObject(por)
        } catch (e: RuntimeException) {
            logger.error("Error storing artifact: ", e)
            throw e
        }
        return path
    }

    private fun getClient(s3ConfigValues: S3Config): AmazonS3 {
        val clientConfiguration = ClientConfiguration()
        clientConfiguration.signerOverride = "AWSS3V4SignerType"
        val builder: AmazonS3ClientBuilder = AmazonS3ClientBuilder
            .standard()
            .withEndpointConfiguration(
                AwsClientBuilder.EndpointConfiguration(
                    s3ConfigValues.endpoint,
                    s3ConfigValues.region
                )
            )
            .withPathStyleAccessEnabled(true)
            .withClientConfiguration(clientConfiguration)
        val s3Client = if (s3ConfigValues.accessKey.isNotEmpty() && s3ConfigValues.secretKey.isNotEmpty()) {
            val credentials: AWSCredentials = BasicAWSCredentials(s3ConfigValues.accessKey, s3ConfigValues.secretKey)
            builder.withCredentials(AWSStaticCredentialsProvider(credentials)).build()
        } else {
            builder.withCredentials(DefaultAWSCredentialsProviderChain.getInstance()).build()
        }
        if (!s3Client.doesBucketExistV2(s3ConfigValues.bucket)) {
            try {
                s3Client.createBucket(s3ConfigValues.bucket)
            } catch (e: AmazonS3Exception) {
                throw Exception(e)
            }
        }

        return s3Client
    }

    companion object {
        //remove once layer1 is updated to not use post in model load
        fun buildLocation(filename: String, extension: String): String {
            val today = LocalDate.now()
            return java.lang.String.format(
                "%s/%s.%s",
                today,
                filename,
                extension
            )
        }
    }
}

data class S3Config(
    val region: String,
    val bucket: String,
    val endpoint: String,
    val accessKey: String,
    val secretKey: String
)

fun getS3ConfigValues(config: ApplicationConfig): S3Config {
    val region = config.propertyOrNull("s3.region")?.getString() ?: ""
    val bucket = config.propertyOrNull("s3.bucket")?.getString() ?: ""
    val endpoint = config.propertyOrNull("s3.endpoint")?.getString() ?: ""
    val accessKey = config.propertyOrNull("s3.access_key")?.getString() ?: ""
    val secretKey = config.propertyOrNull("s3.secret_key")?.getString() ?: ""
    return S3Config(
        region,
        bucket,
        endpoint,
        accessKey,
        secretKey
    )
}
