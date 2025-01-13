package org.openmbee.flexo.mms.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openmbee.flexo.mms.lib.MimeTypes
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3CrtAsyncClientBuilder
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.Upload
import java.io.InputStream
import java.net.URI
import java.time.Duration
import java.time.LocalDate

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
                        location
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
                            path!!
                        )
                        call.respond(s3Storage.getPreSignedUrl(path!!))
                    } catch (e: AwsServiceException) {
                        call.respond(HttpStatusCode(e.statusCode(), e.awsErrorDetails().errorCode()), e.message!!)
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
                    } catch (e: AwsServiceException) {
                        call.respond(HttpStatusCode(e.statusCode(), e.awsErrorDetails().errorCode()), e.message!!)
                    }
                }
            }
        //}
    }
}

class S3Storage(s3Config: S3Config) {
    private val logger = LoggerFactory.getLogger(javaClass)
    //https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html
    private val s3Client = getClient(s3Config)
    private val s3ClientAsync = getClientAsync(s3Config)
    private val presigner = getPresigner()
    private val bucket = s3Config.bucket

    fun get(location: String?): ByteArray {
        //TODO should use async client
        val objectRequest = GetObjectRequest.builder().key(location).bucket(bucket).build()
        return s3Client.getObject(objectRequest).readAllBytes()
    }

    fun getPreSignedUrl(location: String): String {
        val objectRequest: GetObjectRequest = GetObjectRequest.builder()
            .bucket(bucket).key(location).build()
        val presignRequest: GetObjectPresignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(30))
            .getObjectRequest(objectRequest).build()
        return presigner.presignGetObject(presignRequest).url().toExternalForm()
    }

    fun store(data: InputStream, path: String): String {
        //https://github.com/awsdocs/aws-doc-sdk-examples/blob/main/javav2/example_code/s3/src/main/java/com/example/s3/transfermanager/UploadStream.java
        val body = AsyncRequestBody.forBlockingInputStream(null) //null indicates stream is provided later
        val transferManager = S3TransferManager.builder().s3Client(s3ClientAsync).build()
        val upload: Upload = transferManager.upload { builder ->
            builder
                .requestBody(body)
                .putObjectRequest { req -> req.bucket(bucket).key(path) }
                .build()
        }
        body.writeInputStream(data)
        upload.completionFuture().join()
        return path
    }

    private fun getClient(s3ConfigValues: S3Config): S3Client {
        val builder = S3Client.builder().forcePathStyle(true)
            .endpointOverride(URI(s3ConfigValues.endpoint)).region(Region.of(s3ConfigValues.region))
        val s3Client = if (s3ConfigValues.accessKey.isNotEmpty() && s3ConfigValues.secretKey.isNotEmpty()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(s3ConfigValues.accessKey, s3ConfigValues.secretKey)
            )).build()
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create()).build()
        }
        //check if bucket exists and create if it doesn't
        try {
            s3Client.getBucketAcl { r -> r.bucket(s3ConfigValues.bucket) }
        } catch (ase: AwsServiceException) {
            if (ase.statusCode() == HttpStatusCode.NotFound.value) {
                s3Client.createBucket { r -> r.bucket(s3ConfigValues.bucket) }
            } else {
                throw ase
            }
        }
        return s3Client
    }
    private fun getClientAsync(s3ConfigValues: S3Config): S3AsyncClient {
        val builder: S3CrtAsyncClientBuilder = S3AsyncClient.crtBuilder().forcePathStyle(true)
            .endpointOverride(URI(s3ConfigValues.endpoint)).region(Region.of(s3ConfigValues.region))
        val s3Client2 = if (s3ConfigValues.accessKey.isNotEmpty() && s3ConfigValues.secretKey.isNotEmpty()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(s3ConfigValues.accessKey, s3ConfigValues.secretKey)
            )).build()
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create()).build()
        }
        return s3Client2
    }
    private fun getPresigner(): S3Presigner {
        return S3Presigner.builder().s3Client(s3Client).build()
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
