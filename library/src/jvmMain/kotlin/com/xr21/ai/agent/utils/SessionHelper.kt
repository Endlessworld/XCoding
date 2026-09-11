package com.xr21.ai.agent.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

object SessionHelper {

    /**
     * 扫描 saver 文件夹，列出所有会话 threadId。
     * 文件名格式：thread-<threadId>.saver
     */
    suspend fun listSessionIds(folder: Path): Sequence<String> {
        if (!Files.isDirectory(folder)) return emptySequence()
        val stream = withContext(Dispatchers.IO) {
            Files.list(folder)
        }
        stream.use { stream ->
            return stream.map { it.fileName.toString() }
                .filter { it.startsWith("thread-") && it.endsWith(".saver") }
                .map { it.removePrefix("thread-").removeSuffix(".saver") }
                .toList()
                .asSequence()
        }
    }
}
