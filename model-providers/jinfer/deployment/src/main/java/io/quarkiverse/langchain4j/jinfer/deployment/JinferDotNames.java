package io.quarkiverse.langchain4j.jinfer.deployment;

import org.jboss.jandex.DotName;

import com.qxotic.jinfer.langchain4j.JinferChatModel;
import com.qxotic.jinfer.langchain4j.JinferDecisionModel;

import dev.langchain4j.model.audio.TextToSpeechModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import io.quarkiverse.langchain4j.ModelName;

/**
 * DotNames that the core framework does not expose. jinfer produces a {@link TextToSpeechModel}
 * bean itself (there is no TextToSpeech provider SPI in core), so it needs its own DotName.
 */
final class JinferDotNames {

    static final DotName TEXT_TO_SPEECH_MODEL = DotName.createSimple(TextToSpeechModel.class);
    static final DotName MODEL_NAME = DotName.createSimple(ModelName.class);
    static final DotName EMBEDDING_MODEL_LISTENER = DotName.createSimple(EmbeddingModelListener.class);
    static final DotName SCORING_MODEL_LISTENER = DotName.createSimple(ScoringModelListener.class);
    static final DotName CHAT_MODEL_BUILDER = DotName.createSimple(JinferChatModel.Builder.class);
    static final DotName DECISION_MODEL_BUILDER = DotName.createSimple(JinferDecisionModel.Builder.class);

    private JinferDotNames() {
    }
}
