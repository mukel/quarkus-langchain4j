package io.quarkiverse.langchain4j.jinfer.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface TranscriptionModelConfig {

    /**
     * The model to run: a HuggingFace/ModelScope GGUF reference, e.g. {@code owner/repo:Q8_0}, or a path to a local
     * GGUF file, such as {@code ./models/model.gguf}, which is never downloaded.
     */
    Optional<String> model();
}
