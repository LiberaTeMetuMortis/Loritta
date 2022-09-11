package net.perfectdreams.loritta.cinnamon.discord.utils

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.json.*
import java.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

class GoogleVisionOCRClient(private val apiKey: String) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
        }
    }

    private val http = HttpClient(CIO) {}

    suspend fun ocr(image: ByteArray): GoogleVisionResponses {
        val json = buildJsonObject {
            putJsonArray("requests") {
                addJsonObject {
                    putJsonArray("features") {
                        addJsonObject {
                            put("maxResults", 1)
                            put("type", "TEXT_DETECTION")
                        }
                    }

                    putJsonObject("image") {
                        put("content", Base64.getEncoder().encodeToString(image))
                    }
                }
            }
        }

        return ocr(json)
    }

    suspend fun ocr(imageUrl: String): GoogleVisionResponses {
        val json = buildJsonObject {
            putJsonArray("requests") {
                addJsonObject {
                    putJsonArray("features") {
                        addJsonObject {
                            put("maxResults", 1)
                            put("type", "TEXT_DETECTION")
                        }
                    }

                    putJsonObject("image") {
                        putJsonObject("source") {
                            put("imageUri", imageUrl)
                        }
                    }
                }
            }
        }

        return ocr(json)
    }

    private suspend fun ocr(payload: JsonObject): GoogleVisionResponses {
        val response = http.post("https://content-vision.googleapis.com/v1/images:annotate?key=$apiKey&alt=json") {
            userAgent("Google-API-Java-Client Google-HTTP-Java-Client/1.21.0 (gzip)")
            setBody(TextContent(payload.toString(), ContentType.Application.Json))
        }

        val x = response.bodyAsText()
        println(x)
        return json.decodeFromString(x)
    }

    @Serializable
    data class GoogleVisionResponses(
        val responses: List<GoogleVisionResponse>
    )

    @Serializable
    data class GoogleVisionResponse(
        val textAnnotations: List<TextAnnotations>? = null
    )

    @Serializable
    data class TextAnnotations(
        val locale: String? = null,
        val description: String
    )
}