package io.quarkiverse.langchain4j.jinfer.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.jinfer")
public interface LangChain4jJinferBuildConfig {

    /**
     * Chat model related settings.
     */
    ModelBuildConfig chatModel();

    /**
     * Embedding model related settings.
     */
    ModelBuildConfig embeddingModel();

    /**
     * Scoring (rerank) model related settings.
     */
    ModelBuildConfig scoringModel();

    /**
     * Decision model related settings.
     */
    ModelBuildConfig decisionModel();

    /**
     * Audio transcription model related settings.
     */
    ModelBuildConfig audioTranscriptionModel();

    /**
     * Text-to-speech model related settings.
     */
    ModelBuildConfig ttsModel();
}
