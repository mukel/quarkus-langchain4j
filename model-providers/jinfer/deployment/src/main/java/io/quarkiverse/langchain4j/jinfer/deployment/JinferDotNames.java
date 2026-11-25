package io.quarkiverse.langchain4j.jinfer.deployment;

import org.jboss.jandex.DotName;

import com.qxotic.jinfer.langchain4j.JinferChatModel;
import com.qxotic.jinfer.langchain4j.JinferDecisionModel;

import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.audio.TextToSpeechModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;

/**
 * DotNames that the core framework does not expose.
 */
final class JinferDotNames {

    static final DotName TEXT_TO_SPEECH_MODEL = DotName.createSimple(TextToSpeechModel.class);
    static final DotName TOKEN_COUNT_ESTIMATOR = DotName.createSimple(TokenCountEstimator.class);
    static final DotName EMBEDDING_MODEL_LISTENER = DotName.createSimple(EmbeddingModelListener.class);
    static final DotName SCORING_MODEL_LISTENER = DotName.createSimple(ScoringModelListener.class);
    static final DotName CHAT_MODEL_BUILDER = DotName.createSimple(JinferChatModel.Builder.class);
    static final DotName DECISION_MODEL_BUILDER = DotName.createSimple(JinferDecisionModel.Builder.class);

    private JinferDotNames() {
    }
}
