package io.quarkiverse.langchain4j.jinfer.runtime.config;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface TtsModelConfig {

    /**
     * The model to run: a HuggingFace/ModelScope GGUF reference, e.g. {@code owner/repo:Q8_0}, or a path to a local
     * GGUF file, such as {@code ./models/model.gguf}, which is never downloaded.
     */
    Optional<String> model();

    /**
     * The speech speed multiplier (must be greater than {@code 0}).
     */
    OptionalDouble speed();

    /**
     * The maximum number of input characters.
     */
    OptionalInt maxInputChars();

    /**
     * Companion files by capability, each a GGUF reference or a local path, such as {@code voice} for the
     * speaker's voice. A capability the model does not have fails when the model loads.
     */
    @ConfigDocMapKey("capability")
    Map<String, String> companions();
}
