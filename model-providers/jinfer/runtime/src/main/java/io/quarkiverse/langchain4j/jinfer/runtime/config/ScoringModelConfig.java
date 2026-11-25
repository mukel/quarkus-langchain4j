package io.quarkiverse.langchain4j.jinfer.runtime.config;

import java.util.Optional;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ScoringModelConfig {

    /**
     * The model to run: a HuggingFace/ModelScope GGUF reference, e.g. {@code owner/repo:Q8_0}, or a path to a local
     * GGUF file, such as {@code ./models/model.gguf}, which is never downloaded.
     */
    Optional<String> model();

    /**
     * The context capacity in tokens. {@code 0} is the smaller of 4096 and the model's maximum, {@code -1} is the
     * model's maximum.
     */
    OptionalInt contextCapacity();

    /**
     * An optional reranker instruction. Rejected if the reranker has no instruction slot.
     */
    Optional<String> instruction();
}
