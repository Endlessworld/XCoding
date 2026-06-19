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
package com.xr21.ai.agent.acp

import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EmbeddedResourceResource
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.content.Media
import org.springframework.util.MimeType
import java.net.URI

/**
 * Builds a Spring AI [UserMessage] from ACP [ContentBlock] list.
 * Extracted from [AgiAgentSession] for single responsibility.
 */
object UserMessageBuilder {

    fun build(content: List<ContentBlock>): UserMessage {
        val builder = UserMessage.builder()
        val textParts = StringBuilder()

        for (block in content) {
            when (block) {
                is ContentBlock.Text -> {
                    if (block.text.isNotBlank()) textParts.append(block.text)
                }
                is ContentBlock.Image -> {
                    val mediaType = block.mimeType ?: "image/png"
                    builder.media(
                        Media.builder()
                            .data(block.data)
                            .mimeType(MimeType.valueOf(mediaType))
                            .build()
                    )
                }
                is ContentBlock.Audio -> {
                    val mediaType = block.mimeType ?: "audio/wav"
                    builder.media(
                        Media.builder()
                            .data(block.data)
                            .mimeType(MimeType.valueOf(mediaType))
                            .build()
                    )
                }
                is ContentBlock.ResourceLink -> {
                    // ResourceLink: append as text reference with URI
                    val desc = block.description?.let { " ($it)" } ?: ""
                    textParts.append("[${block.name}$desc](${block.uri})")
                    // Also add as media if mimeType is available
                    val linkMimeType = block.mimeType
                    if (linkMimeType != null) {
                        builder.media(
                            Media.builder()
                                .data(URI.create(block.uri))
                                .mimeType(MimeType.valueOf(linkMimeType))
                                .build()
                        )
                    }
                }
                is ContentBlock.Resource -> {
                    when (val resource = block.resource) {
                        is EmbeddedResourceResource.TextResourceContents -> {
                            if (resource.text.isNotBlank()) textParts.append(resource.text)
                            val textMimeType = resource.mimeType
                            if (textMimeType != null) {
                                builder.media(
                                    Media.builder()
                                        .data(resource.text)
                                        .mimeType(MimeType.valueOf(textMimeType))
                                        .build()
                                )
                            }
                        }
                        is EmbeddedResourceResource.BlobResourceContents -> {
                            val mediaType = resource.mimeType ?: "application/octet-stream"
                            builder.media(
                                Media.builder()
                                    .data(resource.blob)
                                    .mimeType(MimeType.valueOf(mediaType))
                                    .build()
                            )
                        }
                    }
                }
            }
        }

        if (textParts.isNotBlank()) {
            builder.text(textParts.toString())
        }
        return builder.build()
    }
}
