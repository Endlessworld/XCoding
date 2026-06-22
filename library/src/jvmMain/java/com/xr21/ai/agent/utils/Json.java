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
package com.xr21.ai.agent.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.SneakyThrows;

import java.util.function.Function;

/**
 *
 * @author Endless
 */
public abstract class Json {

    private static final JsonMapper jsonMapper;

    static {
        jsonMapper = JsonMapper.builder()
                .addModules(ObjectMapper.findModules())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
                .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
                .build();
    }

    public static <T> T jsonMapper(Function<JsonMapper,T> function) {
        return  function.apply(jsonMapper);
    }

    @SneakyThrows(value = JsonProcessingException.class)
    public static <T> String toJson(T value) {
        return jsonMapper.writeValueAsString(value);
    }

    @SneakyThrows(value = JsonProcessingException.class)
    public static <T> String toPrettyJson(T value) {
        return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    @SneakyThrows(value = JsonProcessingException.class)
    public static <R> R to(String value, Class<R> clazz) {
        return jsonMapper.readValue(value, clazz);
    }

    @SneakyThrows(value = JsonProcessingException.class)
    public static <T, R> R to(T value, Class<R> clazz) {
        return jsonMapper.readValue(toJson(value), clazz);
    }

    @SneakyThrows(value = JsonProcessingException.class)
    public static <T> T to(String value, TypeReference<T> valueTypeRef) {
        return jsonMapper.readValue(value, valueTypeRef);
    }
}