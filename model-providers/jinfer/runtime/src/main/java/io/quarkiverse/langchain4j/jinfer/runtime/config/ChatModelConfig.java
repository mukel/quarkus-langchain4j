package io.quarkiverse.langchain4j.jinfer.runtime.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

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
     * The sampling temperature.
     */
    @ConfigDocDefault("the model's recommended value")
    @WithDefault("${quarkus.langchain4j.temperature}")
    OptionalDouble temperature();

    /**
     * The nucleus sampling {@code top-p} value.
     */
    OptionalDouble topP();

    /**
     * The {@code top-k} sampling value.
     */
    OptionalInt topK();

    /**
     * The {@code min-p} sampling value.
     */
    OptionalDouble minP();

    /**
     * The maximum number of tokens to generate.
     */
    OptionalInt maxOutputTokens();

    /**
     * The maximum number of reasoning tokens. {@code -1} uncaps reasoning.
     */
    OptionalInt maxReasoningTokens();

    /**
     * The RNG seed for sampling.
     */
    OptionalLong seed();

    /**
     * Path to a read-only cached-prompt artifact to mount.
     */
    Optional<Path> promptCache();

    /**
     * Whether to enable the model's "thinking"/reasoning mode.
     */
    Optional<Boolean> thinking();

    /**
     * Companion files by capability, each a GGUF reference or a local path, such as {@code media} for a vision
     * or audio projector (mmproj). A capability the model does not have fails when the model loads.
     */
    @ConfigDocMapKey("capability")
    Map<String, String> companions();
}
