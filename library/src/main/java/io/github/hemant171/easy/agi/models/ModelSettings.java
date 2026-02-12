package io.github.hemant171.easy.agi.models;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

/**
 * ModelSettings is a configuration class that holds various parameters
 * for configuring AI language models.
 *
 * <p>It supports builder pattern for easy configuration:</p>
 * <pre>{@code
 * ModelSettings settings = ModelSettings.builder()
 *     .temperature(0.1)
 *     .maxTokens(1000)
 *     .build();
 * }</pre>
 *
 * @see AiModel
 * @see ChatLanguageModel
 */
@Getter
@ToString
@Builder(builderClassName = "ModelSettingsBuilder")
public class ModelSettings {

    /**
     * Temperature controls randomness in the model's output.
     * Lower values (e.g., 0.0 - 0.3) make output more deterministic and focused.
     * Higher values (e.g., 0.7 - 1.0) make output more random and creative.
     * Default: null (uses model's default)
     */
    private final Double temperature;

    /**
     * Maximum number of tokens to generate in the response.
     * Higher values allow longer responses but may increase cost.
     * Default: null (uses model's default)
     */
    private final Integer maxTokens;

    /**
     * Top-p (nucleus sampling) controls diversity.
     * Lower values (e.g., 0.1) make output more focused.
     * Higher values (e.g., 0.9) allow more diversity.
     * Default: null (uses model's default)
     */
    private final Double topP;

    /**
     * Penalizes new tokens based on their frequency in the text so far.
     * Higher values discourage repetition.
     * Default: null (uses model's default)
     */
    private final Double frequencyPenalty;

    /**
     * Penalizes new tokens based on whether they appear in the text so far.
     * Higher values discourage repetition of existing phrases.
     * Default: null (uses model's default)
     */
    private final Double presencePenalty;

    /**
     * Seed for random number generation, enabling reproducible outputs.
     * Default: null (random behavior)
     */
    private final Integer seed;

    /**
     * System message to set the behavior/persona of the assistant.
     * Default: null
     */
    private final String systemMessage;

    /**
     * Stop sequences that will cause the model to stop generating.
     * Default: null
     */
    private final java.util.List<String> stopSequences;

    /**
     * Additional custom parameters that can be passed to the model.
     * Useful for provider-specific parameters not covered above.
     */
    @Builder.Default
    private final Map<String, Object> additionalParams = new HashMap<>();

    /**
     * Creates a ModelSettings with default values.
     */
    public ModelSettings() {
        this.temperature = null;
        this.maxTokens = null;
        this.topP = null;
        this.frequencyPenalty = null;
        this.presencePenalty = null;
        this.seed = null;
        this.systemMessage = null;
        this.stopSequences = null;
        this.additionalParams = new HashMap<>();
    }

    /**
     * Private constructor used by the builder.
     */
    private ModelSettings(Double temperature, Integer maxTokens, Double topP,
                          Double frequencyPenalty, Double presencePenalty, Integer seed,
                          String systemMessage, java.util.List<String> stopSequences,
                          Map<String, Object> additionalParams) {
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topP = topP;
        this.frequencyPenalty = frequencyPenalty;
        this.presencePenalty = presencePenalty;
        this.seed = seed;
        this.systemMessage = systemMessage;
        this.stopSequences = stopSequences;
        this.additionalParams = additionalParams != null ? additionalParams : new HashMap<>();
    }

    /**
     * Creates a new builder for ModelSettings.
     *
     * @return a new ModelSettingsBuilder
     */
    public static ModelSettingsBuilder builder() {
        return new ModelSettingsBuilder();
    }

    /**
     * Builder class for ModelSettings.
     */
    public static class ModelSettingsBuilder {
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer seed;
        private String systemMessage;
        private java.util.List<String> stopSequences;
        private Map<String, Object> additionalParams = new HashMap<>();

        ModelSettingsBuilder() {
        }

        public ModelSettingsBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public ModelSettingsBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public ModelSettingsBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public ModelSettingsBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public ModelSettingsBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public ModelSettingsBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public ModelSettingsBuilder systemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }

        public ModelSettingsBuilder stopSequences(java.util.List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public ModelSettingsBuilder additionalParam(String key, Object value) {
            this.additionalParams.put(key, value);
            return this;
        }

        public ModelSettingsBuilder additionalParams(Map<String, Object> additionalParams) {
            this.additionalParams = additionalParams;
            return this;
        }

        public ModelSettings build() {
            return new ModelSettings(temperature, maxTokens, topP, frequencyPenalty,
                    presencePenalty, seed, systemMessage, stopSequences, additionalParams);
        }
    }
}
