package com.maku.idleharvest.buyer

import com.maku.idleharvest.domain.models.EarningEvent
import com.maku.idleharvest.domain.models.EarningSource
import com.maku.idleharvest.domain.models.EarningsSummary
import com.maku.idleharvest.domain.models.HardwareProfile
import com.maku.idleharvest.domain.models.InferenceBackend
import com.maku.idleharvest.domain.models.ModelMetadata
import com.maku.idleharvest.domain.models.ModelPurpose
import com.maku.idleharvest.domain.models.QuantizationLevel
import com.maku.idleharvest.infrastructure.currentTimeMillis
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

private val JsonConfig =
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

private val DemoModel =
    ModelMetadata(
        id = "idleharvest_model",
        version = "1.0.0",
        purpose = ModelPurpose.RESOURCE_OPTIMIZATION,
        sizeBytes = 8_000_000L,
        quantization = QuantizationLevel.INT8,
        targetHardware =
        HardwareProfile(
            architecture = "arm64-v8a",
            minCores = 4,
            minRamGb = 4f,
            supportedBackends =
            listOf(
                InferenceBackend.KLEIDIAI,
                InferenceBackend.XNNPACK,
                InferenceBackend.CPU_BASELINE,
            ),
        ),
        sha256Checksum = "",
        isStable = true,
    )

private object BuyerBackendConstants {
    const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    const val WEEK_MILLIS = 7 * DAY_MILLIS
    const val MISSING_ARTIFACT_MESSAGE =
        "Artifact missing. Copy the exported .pte into buyerBackend/artifacts/%s.pte " +
            "or ml/output/%s.pte."
}

private val buyerEvents =
    mutableListOf(
        EarningEvent(
            id = "buyer_seed_1",
            source = EarningSource.MESH_SERVICE,
            amountUsdc = 0.18,
            amountLocal = 250.0,
            localCurrency = "NGN",
            agentId = com.maku.idleharvest.domain.models.AgentId("buyer-backend"),
            timestamp = currentTimeMillis() - 15 * 60 * 1000L,
        ),
    )

fun main(args: Array<String>) {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0", module = Application::buyerModule)
        .start(wait = true)
}

fun Application.buyerModule() {
    install(DefaultHeaders)
    install(ContentNegotiation) {
        json(JsonConfig)
    }

    routing {
        healthRoute()
        buyerApiRoutes()
        modelApiRoutes()
    }
}

private fun io.ktor.server.routing.Routing.healthRoute() {
    get("/health") {
        call.respond(
            mapOf(
                "status" to "ok",
                "service" to "buyer-backend",
                "timestamp" to currentTimeMillis(),
            ),
        )
    }
}

private fun io.ktor.server.routing.Routing.buyerApiRoutes() {
    route("/api/buyer") {
        get("/summary") {
            val summary = buyerEvents.toEarningsSummary()
            call.respond(
                BuyerSummaryResponse(
                    summary = summary,
                    recentEvents = buyerEvents.sortedByDescending { it.timestamp },
                ),
            )
        }

        post("/orders") {
            val request = call.receive<BuyerOrderRequest>()
            val event = request.toBuyerEvent()
            buyerEvents += event
            call.respond(
                BuyerOrderResponse(
                    orderId = event.id,
                    status = "accepted",
                    payoutUsdc = request.offerAmountUsdc,
                ),
            )
        }
    }
}

private fun io.ktor.server.routing.Routing.modelApiRoutes() {
    route("/api/models") {
        get {
            call.respond(listOf(DemoModel))
        }

        get("/{modelId}") {
            val modelId = call.parameters["modelId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            if (modelId != DemoModel.id) {
                return@get call.respond(HttpStatusCode.NotFound)
            }
            call.respond(DemoModel.withResolvedChecksum(resolveArtifactFile(DemoModel.id)))
        }

        get("/{modelId}.pte") {
            val modelId = call.parameters["modelId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            if (modelId != DemoModel.id) {
                return@get call.respond(HttpStatusCode.NotFound)
            }
            val artifact = resolveArtifactFile(modelId)
            if (!artifact.exists()) {
                return@get call.respondText(
                    text = BuyerBackendConstants.MISSING_ARTIFACT_MESSAGE.format(modelId, modelId),
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.NotFound,
                )
            }
            call.respondFile(artifact)
        }
    }
}

private fun BuyerOrderRequest.toBuyerEvent(): EarningEvent = EarningEvent(
    id = "buyer_${modelId}_${currentTimeMillis()}",
    source = EarningSource.MESH_SERVICE,
    amountUsdc = offerAmountUsdc,
    amountLocal = null,
    localCurrency = null,
    agentId = com.maku.idleharvest.domain.models.AgentId(buyerId),
    timestamp = currentTimeMillis(),
)

@Serializable
data class BuyerOrderRequest(
    val buyerId: String,
    val modelId: String,
    val offerAmountUsdc: Double,
)

@Serializable
data class BuyerOrderResponse(
    val orderId: String,
    val status: String,
    val payoutUsdc: Double,
)

@Serializable
data class BuyerSummaryResponse(
    val summary: EarningsSummary,
    val recentEvents: List<EarningEvent>,
)

private fun ModelMetadata.withResolvedChecksum(artifact: File): ModelMetadata {
    if (!artifact.exists()) return this
    return copy(
        sizeBytes = artifact.length(),
        sha256Checksum = sha256(artifact.readBytes()),
    )
}

private fun resolveArtifactFile(modelId: String): File {
    val candidates =
        listOf(
            File("buyerBackend/artifacts/$modelId.pte"),
            File("ml/output/$modelId.pte"),
            File("../ml/output/$modelId.pte"),
        )
    return candidates.firstOrNull { it.exists() } ?: candidates.first()
}

private fun List<EarningEvent>.toEarningsSummary(): EarningsSummary {
    val now = currentTimeMillis()
    val bySource =
        groupBy { it.source }.mapValues { (_, events) ->
            events.sumOf { it.amountUsdc }
        }
    return EarningsSummary(
        totalEarnedUsdc = sumOf { it.amountUsdc },
        last24hUsdc = filter { it.timestamp >= now - BuyerBackendConstants.DAY_MILLIS }.sumOf { it.amountUsdc },
        last7dUsdc = filter { it.timestamp >= now - BuyerBackendConstants.WEEK_MILLIS }.sumOf { it.amountUsdc },
        bySource = bySource,
    )
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte -> "%02x".format(byte) }
