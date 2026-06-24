package com.maku.idleharvest.buyer

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuyerBackendArtifactRouteTest {
    @Test
    fun servesTrainedModelArtifactBytes() = testApplication {
        application {
            buyerModule()
        }

        val artifact = resolveArtifactFile()
        val response = client.get("/api/models/idleharvest_model.pte")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsBytes()
        assertTrue(body.isNotEmpty(), "Expected the .pte response body to contain bytes")
        assertEquals(artifact.length(), body.size.toLong(), "Expected the served artifact size to match disk")
        assertContentEquals(artifact.readBytes(), body, "Expected the backend to serve the exact trained artifact")

        val expectedChecksum = sha256Hex(artifact.readBytes())
        assertTrue(expectedChecksum.isNotBlank())
    }

    @Test
    fun servesModelMetadataForTrainedArtifact() = testApplication {
        application {
            buyerModule()
        }

        val artifact = resolveArtifactFile()
        val response = client.get("/api/models/idleharvest_model")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsBytes().decodeToString()
        assertTrue(body.contains("\"sizeBytes\": ${artifact.length()}"))
        assertTrue(body.contains("\"sha256Checksum\":"))
    }

    private fun resolveArtifactFile(): File {
        val candidates =
            listOf(
                File("ml/output/idleharvest_model.pte"),
                File("../ml/output/idleharvest_model.pte"),
                File("../../ml/output/idleharvest_model.pte"),
            )
        return candidates.firstOrNull { it.exists() }
            ?: error("Expected trained model artifact at one of: ${candidates.joinToString { it.path }}")
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
