package io.quarkiverse.langchain4j.jinfer.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.jinfer")
public interface LangChain4jJinferConfig {

    /**
     * Default model config.
     */
    @WithParentName
    JinferConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, JinferConfig> namedConfig();

    interface JinferConfig {

        /**
         * Whether to enable the models. Set to {@code false} to disable them: they then throw when used, which is
         * useful for tests.
         */
        @WithDefault("true")
        boolean enableIntegration();

        /**
         * Whether to download and load this configuration's models while the application starts, instead of on first
         * use. A model that cannot be loaded then stops the application from starting.
         */
        @WithDefault("false")
        boolean loadAtStartup();

        /**
         * The wall-clock deadline of each chat request, streaming included. A reply it cuts fails with
         * LangChain4j's {@code TimeoutException}.
         */
        @ConfigDocDefault("no timeout")
        Optional<Duration> timeout();

        /**
         * Chat model related settings.
         */
        ChatModelConfig chatModel();

        /**
         * Embedding model related settings.
         */
        EmbeddingModelConfig embeddingModel();

        /**
         * Scoring (rerank) model related settings.
         */
        ScoringModelConfig scoringModel();

        /**
         * Decision model related settings.
         */
        DecisionModelConfig decisionModel();

        /**
         * Audio transcription model related settings.
         */
        TranscriptionModelConfig audioTranscriptionModel();

        /**
         * Text-to-speech model related settings.
         */
        TtsModelConfig ttsModel();
    }
}
