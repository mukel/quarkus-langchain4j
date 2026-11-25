package io.quarkiverse.langchain4j.jinfer.runtime.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * The HuggingFace/ModelScope GGUF model reference, e.g. {@code owner/repo:Q8_0}.
     * Required unless {@link #modelPath()} is set.
     */
    Optional<String> model();

    /**
     * Path to a local GGUF file. Takes precedence over {@link #model()}; when set, no download occurs.
     */
    Optional<Path> modelPath();

    /**
     * The context capacity in tokens. {@code 0} is the smaller of 4096 and the model's maximum, {@code -1} is the
     * model's maximum.
     */
    OptionalInt contextCapacity();

    /**
     * The width of the embedding vectors, for models trained with Matryoshka representation learning.
     * When unset, the model's own width is used. A width the model cannot produce fails when the model is created.
     */
    OptionalInt dimensions();

    /**
     * Companion files by capability, each a GGUF reference or a local path, such as {@code media} for a vision
     * or audio projector (mmproj). A capability the model does not have fails when the model loads.
     */
    @ConfigDocMapKey("capability")
    Map<String, String> companions();
}
