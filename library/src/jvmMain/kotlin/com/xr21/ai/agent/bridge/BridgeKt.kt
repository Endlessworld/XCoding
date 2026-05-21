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
package com.xr21.ai.agent.bridge

import com.agentclientprotocol.model.ToolCallLocation
import kotlinx.serialization.json.JsonElement

/**
 * Bridge functions for Java tools to create ACP model types that have Kotlin-specific types (e.g. UInt).
 */
object BridgeKt {

    @JvmStatic
    @JvmOverloads
    fun createToolCallLocation(path: String, line: Int = 0, _meta: JsonElement? = null): ToolCallLocation {
        return ToolCallLocation(
            path = path,
            line = if (line >= 0) line.toUInt() else null,
            _meta = _meta
        )
    }
    @JvmStatic
    fun getLine(location: ToolCallLocation): Int {
        return location.line?.toInt() ?: 0
    }
}