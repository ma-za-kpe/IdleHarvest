package com.maku.idleharvest.infrastructure

import android.content.Context
import com.maku.idleharvest.BuildConfig
import com.maku.idleharvest.domain.models.ModelMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AndroidModelDownloader(
    context: Context,
    private val baseUrl: String = BuildConfig.MODEL_ARTIFACT_BASE_URL,
) : ModelDownloader {
    private val modelRoot = File(context.filesDir, "models").apply { mkdirs() }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }

    override suspend fun download(
        modelId: String,
        metadata: ModelMetadata,
        resumeFromByte: Long,
        onProgress: suspend (bytesDownloaded: Long) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val destinationDir = File(modelRoot, modelId).apply { mkdirs() }
        val destinationFile = File(destinationDir, "${metadata.version}.pte")
        val tempFile = File(destinationDir, "${metadata.version}.pte.part")
        val sourceUrl = URL("${baseUrl.trimEnd('/')}/$modelId.pte")

        val startingOffset =
            if (resumeFromByte > 0L && tempFile.exists()) {
                tempFile.length().coerceAtLeast(resumeFromByte)
            } else {
                0L
            }

        if (startingOffset == 0L) {
            tempFile.delete()
        }

        val connection = sourceUrl.openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        if (startingOffset > 0L) {
            connection.setRequestProperty("Range", "bytes=$startingOffset-")
        }

        try {
            connection.inputStream.use { input ->
                FileOutputStream(tempFile, startingOffset > 0L).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = startingOffset
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded)
                    }
                }
            }

            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }

            DownloadResult(
                filePath = destinationFile.absolutePath,
                sizeBytes = destinationFile.length(),
            )
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun readFileContent(filePath: String): ByteArray? = withContext(Dispatchers.IO) {
        File(filePath).takeIf { it.exists() }?.readBytes()
    }
}
