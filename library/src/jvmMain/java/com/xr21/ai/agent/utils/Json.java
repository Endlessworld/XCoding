package com.xr21.ai.agent.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public abstract class Json {

    private static final JsonMapper jsonMapper;

    static {
        jsonMapper = JsonMapper.builder().addModules(ObjectMapper.findModules()).build();
        jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }


    public static <T> String toJson(T value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> String toPrettyJson(T value) {
        try {
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <R> R to(String value, Class<R> clazz) {
        try {
            return jsonMapper.readValue(value, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T, R> R to(T value, Class<R> clazz) {
        try {
            return jsonMapper.readValue(toJson(value), clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}