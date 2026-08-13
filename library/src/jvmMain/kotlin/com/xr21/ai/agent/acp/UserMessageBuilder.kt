/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("LombokKotlinCompilerPlugin")

package com.xr21.ai.agent.acp

import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EmbeddedResourceResource
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.content.Media
import org.springframework.util.MimeType
import org.springframework.util.StringUtils
import java.net.URI
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.extension


/**
 * Builds a Spring AI [UserMessage] from ACP [ContentBlock] list.
 *
 * Strategy for constructing the most suitable UserMessage:
 * - Text blocks → accumulated into textContent
 * - Image/Audio blocks with base64 data → Media with byte[] data (LLM-native multimodal)
 * - ResourceLink (file references) → read file content for text files, add as Media for binary files
 * - Resource (embedded) → TextResourceContents goes to textContent, BlobResourceContents goes to Media
 * - MIME type is inferred from file extension when not provided by the client
 */
object UserMessageBuilder {

    private val logger = org.slf4j.LoggerFactory.getLogger(UserMessageBuilder::class.java)

    /** Extensions that are safe to inline as text content */
    private val TEXT_EXTENSIONS = setOf(
        "txt",
        "md",
        "java",
        "kt",
        "kts",
        "py",
        "js",
        "ts",
        "jsx",
        "tsx",
        "xml",
        "json",
        "yaml",
        "yml",
        "toml",
        "ini",
        "cfg",
        "conf",
        "gradle",
        "properties",
        "sh",
        "bat",
        "ps1",
        "sql",
        "html",
        "css",
        "csv",
        "log",
        "c",
        "cpp",
        "h",
        "hpp",
        "rs",
        "go",
        "rb",
        "php",
        "swift",
        "scala",
        "clj",
        "elm",
        "erl",
        "ex",
        "exs",
        "fs",
        "fsx",
        "groovy",
        "hs",
        "lua",
        "pl",
        "pm",
        "r",
        "R",
        "rmd",
        "tex",
        "vue",
        "svelte",
        "astro",
        "proto",
        "graphql",
        "cmake",
        "makefile",
        "dockerfile",
        "gitignore",
        "env",
        "editorconfig"
    )

    /** MIME type mapping for image extensions */
    private val IMAGE_EXTENSION_MIME_MAP = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
        "ico" to "image/x-icon"
    )

    /** MIME type mapping for non-image common extensions */
    private val EXTENSION_MIME_MAP = mapOf(
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "flac" to "audio/flac",
        "aac" to "audio/aac",
        "m4a" to "audio/mp4",
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "mkv" to "video/x-matroska",
        "pdf" to "application/pdf",
        "zip" to "application/zip",
        "gz" to "application/gzip",
        "tar" to "application/x-tar"
    )

    fun buildUserMessage(content: List<ContentBlock>): UserMessage {
        val textParts = StringBuilder()
        val mediaList = mutableListOf<Media>()

        for (block in content) {
            logger.info("input contentBlock : {}", block)
            when (block) {
                is ContentBlock.Text -> {
                    if (block.text.isNotBlank()) {
                        if (textParts.isNotEmpty()) textParts.append("\n")
                        textParts.append(block.text)
                    }
                }

                is ContentBlock.Image -> {
                    val media = buildImageMedia(block)
                    if (media != null) mediaList.add(media)
                }

                is ContentBlock.Audio -> {
                    val media = buildAudioMedia(block)
                    if (media != null) mediaList.add(media)
                }

                is ContentBlock.ResourceLink -> {
                    processResourceLink(block, textParts, mediaList)
                }

                is ContentBlock.Resource -> {
                    processResource(block, textParts, mediaList)
                }
            }
        }

        val builder = UserMessage.builder()
        builder.metadata(mutableMapOf<String, Any>())
        if (textParts.isNotBlank()) {
            builder.text(textParts.toString())
        }
        if (mediaList.isNotEmpty()) {
            builder.media(mediaList)
        }
        val message = builder.build()
        logger.info("final userMessage text: {}", message.text)
        for (media in mediaList) {
            logger.info("final media : {} {}", media.name, media.mimeType)
        }

        return message;
    }

    // ─── Image ───────────────────────────────────────────────

    private fun buildImageMedia(block: ContentBlock.Image): Media? {
        val mimeType = resolveMimeType(block.mimeType, block.uri, "image/png")
        val data = resolveImageData(block)
        if (data == null) {
            logger.warn("Image block has no data or readable uri: uri={}", block.uri)
            return null
        }
        return Media.builder().mimeType(MimeType.valueOf(mimeType)).data(data).build()
    }

    private fun resolveImageData(block: ContentBlock.Image): Any? {
        // Prefer inline base64 data
        if (block.data.isNotBlank()) {
            return try {
                Base64.getDecoder().decode(block.data)
            } catch (e: IllegalArgumentException) {
                // Not valid base64, treat as raw string (e.g. URL)
                block.data
            }
        }
        // Fall back to reading from URI
        if (!block.uri.isNullOrBlank()) {
            return readFileBytes(block.uri!!)
        }
        return null
    }

    /**
     * 构建媒体对象
     *
     * @param mimeTypeStr MIME 类型字符串
     * @param data        Base64 编码的数据
     * @param uri         资源 URI
     * @return Media 对象，如果无法构建则返回 null
     */
    private fun buildMediaFromContent(data: String?, uri: String?): Media? {
        try {
            logger.info("[AcpAgent] buildMediaFromContent {} {}", data, uri)
            // 解析 MIME 类型
            val contentType = URLConnection.guessContentTypeFromName(uri)
            if (contentType == null) {
                return Media.builder().mimeType(MimeType.valueOf("application/octet-stream")).data(URI.create(uri)).build()
            }
            val mimeType = MimeType.valueOf(contentType)
            val mediaBuilder = Media.builder().mimeType(mimeType)
            // 优先使用 Base64 数据
            if (StringUtils.hasText(data)) {
                val decodedData = Base64.getDecoder().decode(data)
                mediaBuilder.data(decodedData)
            } else if (StringUtils.hasText(uri)) {
                if (mimeType.getType().contains("image")) {
                    logger.info("[AcpAgent] load image: $mimeType from $uri")
                    val imagePath = Paths.get(URI.create(uri))
                    val imageBytes = Files.readAllBytes(imagePath)
                    val base64Image = Base64.getEncoder().encodeToString(imageBytes)
                    val decodedData = Base64.getDecoder().decode(base64Image)
                    mediaBuilder.data(decodedData)
                }
            } else {
                logger.warn("[AcpAgent] Cannot build media: both data and uri are empty")
                return null
            }
            return mediaBuilder.build()
        } catch (e: Throwable) {
            logger.error("[AcpAgent] Failed to build media", e)
            return null
        }
    }
    // ─── Audio ───────────────────────────────────────────────

    private fun buildAudioMedia(block: ContentBlock.Audio): Media? {
        val mimeType = resolveMimeType(block.mimeType, null, "audio/wav")
        val data = if (block.data.isNotBlank()) {
            try {
                Base64.getDecoder().decode(block.data)
            } catch (e: IllegalArgumentException) {
                block.data
            }
        } else {
            logger.warn("Audio block has no data")
            return null
        }
        return Media.builder().mimeType(MimeType.valueOf(mimeType)).data(data).build()
    }

    // ─── ResourceLink ────────────────────────────────────────

    private fun processResourceLink(
        block: ContentBlock.ResourceLink, textParts: StringBuilder, mediaList: MutableList<Media>
    ) {
        val name = block.name
        val uri = block.uri
        val description = block.description

        // Build a text reference header
        if (textParts.isNotEmpty()) textParts.append("\n\n")
        val descSuffix = if (!description.isNullOrBlank()) " — $description" else ""
        textParts.append("--- File: $name$descSuffix ---\n")

        // Try to read the file content
        val filePath = uriToPath(uri)
        if (filePath != null) {
            val inferredMime = inferMimeTypeFromPath(filePath)
            val isText =
                inferredMime != null && inferredMime.startsWith("text/") || filePath.extension.lowercase() in TEXT_EXTENSIONS

            if (isText) {
                // Read and inline text content
                val fileContent = readFileText(uri)
                if (fileContent != null) {
                    textParts.append(fileContent)
                    textParts.append("\n--- End of file: $name ---")
                } else {
                    textParts.append("[Unable to read file: $uri]")
                }
            } else {
                // Binary file: add as Media
                val uri = block.uri
                val mimeType = MimeType.valueOf(resolveMimeType(null, block.uri, "application/octet-stream"))
                if (mimeType.getType().contains("image")) {
                    // 处理资源链接
                    val media = buildMediaFromContent(null, block.uri)
                    if (media != null) {
                        mediaList.add(media)
                    } else {
                        textParts.append("\n[Binary file attached as media: $name ($mimeType) $uri]")
                        textParts.append(block.uri)
                    }
                }
            }
        } else {
            // Non-file URI (e.g. http://) — just reference it
            textParts.append("[Resource: $uri]")
        }
    }

    // ─── Resource (embedded) ─────────────────────────────────

    private fun processResource(
        block: ContentBlock.Resource, textParts: StringBuilder, mediaList: MutableList<Media>
    ) {
        when (val resource = block.resource) {
            is EmbeddedResourceResource.TextResourceContents -> {
                // Text resources: append to text content only (not as Media)
                val text = resource.text
                val uri = resource.uri
                if (text.isNotBlank()) {
                    if (textParts.isNotEmpty()) textParts.append("\n")
                    if (!uri.isNullOrBlank()) {
                        textParts.append("--- Embedded: $uri ---\n")
                    }
                    textParts.append(text)
                }
            }

            is EmbeddedResourceResource.BlobResourceContents -> {
                // Binary resources: add as Media
                val mimeType = resolveMimeType(resource.mimeType, resource.uri, "application/octet-stream")
                val data = if (!resource.blob.isNullOrBlank()) {
                    try {
                        Base64.getDecoder().decode(resource.blob)
                    } catch (e: IllegalArgumentException) {
                        resource.blob.toByteArray()
                    }
                } else {
                    logger.warn("BlobResourceContents has no blob data")
                    return
                }
                val name = resource.uri?.let { extractFileName(it) } ?: "blob"
                mediaList.add(
                    Media.builder().mimeType(MimeType.valueOf(mimeType)).data(data).name(sanitizeMediaName(name))
                        .build()
                )
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────

    /** Resolve MIME type: prefer explicit, then infer from URI, then fallback */
    private fun resolveMimeType(explicit: String?, uri: String?, fallback: String): String {
        if (!explicit.isNullOrBlank()) return explicit
        if (!uri.isNullOrBlank()) {
            val inferred = inferMimeTypeFromUri(uri)
            if (inferred != null) return inferred
        }
        return fallback
    }

    private fun inferMimeTypeFromUri(uri: String): String? {
        val path = uriToPath(uri) ?: return extensionFromUriString(uri)?.let { ext ->
            IMAGE_EXTENSION_MIME_MAP[ext] ?: EXTENSION_MIME_MAP[ext]
        }
        return inferMimeTypeFromPath(path)
    }

    private fun inferMimeTypeFromPath(path: Path): String? {
        val ext = path.extension.lowercase()
        return IMAGE_EXTENSION_MIME_MAP[ext] ?: EXTENSION_MIME_MAP[ext]
    }

    private fun extensionFromUriString(uri: String): String? {
        val pathPart = uri.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val dotIndex = pathPart.lastIndexOf('.')
        if (dotIndex < 0) return null
        return pathPart.substring(dotIndex + 1).lowercase()
    }

    /** Convert a URI string to a local Path, or null if not a file:// URI */
    private fun uriToPath(uri: String): Path? {
        return try {
            val javaUri = URI.create(uri)
            if (javaUri.scheme == "file" || javaUri.scheme == null) {
                Paths.get(javaUri)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readFileText(uri: String): String? {
        return try {
            val path = uriToPath(uri) ?: return null
            val file = path.toFile()
            if (file.isFile && file.canRead()) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn("Failed to read file: $uri", e)
            null
        }
    }

    private fun readFileBytes(uri: String): ByteArray? {
        return try {
            val path = uriToPath(uri) ?: return null
            val file = path.toFile()
            if (file.isFile && file.canRead()) {
                file.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn("Failed to read file bytes: $uri", e)
            null
        }
    }

    private fun extractFileName(uri: String): String {
        return uri.substringAfterLast('/').substringBefore('?').substringBefore('#')
    }

    /** Sanitize a name for use as Media.name (alphanumeric, whitespace, hyphens, parens, brackets) */
    private fun sanitizeMediaName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9\\s\\-\\(\\)\\[\\]._-]"), "_").replace(Regex("\\s{2,}"), " ").trim()
            .ifBlank { "attachment" }
    }
}
