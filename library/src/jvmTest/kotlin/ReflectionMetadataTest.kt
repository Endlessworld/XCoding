package io.github.kotlin.fibonacci

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlin.test.Test

class ReflectionMetadataTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun transformReflectionMetadata() {
        val inputPath = "src/jvmMain/resources/META-INF/native-image/reachability-metadata.json"
        val outputPath = "src/jvmMain/resources/META-INF/native-image/reachability-metadata-transformed.json"

        // 读取JSON文件
        val jsonNode = objectMapper.readTree(java.io.File(inputPath))

        // 获取reflection数组
        val reflectionArray = jsonNode.get("reflection")

        if (reflectionArray != null && reflectionArray.isArray) {
            val arrayNode = reflectionArray as ArrayNode
            
            // 遍历reflection数组中的每个元素
            for (i in 0 until arrayNode.size()) {
                val element = arrayNode.get(i)

                // 获取type值
                val typeValue = element.get("type")?.asText() ?: continue

                // 创建新的对象，只保留type并添加新的键值对
                val newElement = objectMapper.createObjectNode() as ObjectNode
                newElement.put("type", typeValue)
                newElement.put("allDeclaredConstructors", true)
                newElement.put("allPublicConstructors", true)
                newElement.put("allDeclaredMethods", true)
                newElement.put("allPublicMethods", true)
                newElement.put("allDeclaredFields", true)
                newElement.put("allPublicFields", true)

                // 替换原数组中的元素
                arrayNode.set(i, newElement)
            }
        }

        // 写入到新文件
        val outputFile = java.io.File(outputPath)
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, jsonNode)

        println("转换完成！已保存到: $outputPath")
    }
}