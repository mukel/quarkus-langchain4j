package io.quarkiverse.langchain4j.jinfer.runtime;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import com.qxotic.jinfer.hub.ModelStore;
import com.qxotic.jinfer.langchain4j.JinferChatModel;
import com.qxotic.jinfer.langchain4j.JinferDecisionModel;
import com.qxotic.jinfer.langchain4j.JinferEmbeddingModel;
import com.qxotic.jinfer.langchain4j.JinferScoringModel;
import com.qxotic.jinfer.langchain4j.JinferSpeechModel;
import com.qxotic.jinfer.langchain4j.JinferTranscriptionModel;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import dev.langchain4j.model.audio.TextToSpeechModel;
import dev.langchain4j.model.audio.TextToSpeechRequest;
import dev.langchain4j.model.audio.TextToSpeechResponse;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.jinfer.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.jinfer.runtime.config.DecisionModelConfig;
import io.quarkiverse.langchain4j.jinfer.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.jinfer.runtime.config.LangChain4jJinferConfig;
import io.quarkiverse.langchain4j.jinfer.runtime.config.ScoringModelConfig;
import io.quarkiverse.langchain4j.jinfer.runtime.config.TranscriptionModelConfig;
import io.quarkiverse.langchain4j.jinfer.runtime.config.TtsModelConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.ImageMode;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.smallrye.config.ConfigValidationException;
import io.smallrye.mutiny.infrastructure.Infrastructure;

@Recorder
public class JinferRecorder {

    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<JinferChatModel.Builder>>> CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<DecisionModelListener>> DECISION_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<JinferDecisionModel.Builder>>> DECISION_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    // Not the JinferModels monitor: a download can take minutes, and shutdown must not wait for it.
    private static final Object CHAT_LOAD_LOCK = new Object();

    private final RuntimeValue<LangChain4jJinferConfig> runtimeConfig;

    public JinferRecorder(RuntimeValue<LangChain4jJinferConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    /** Closes every loaded model once, at application shutdown, releasing their memory-mapped weights. */
    public void closeModelsAtShutdown(ShutdownContext shutdown) {
        shutdown.addLastShutdownTask(new Runnable() {
            @Override
            public void run() {
                JinferModels.closeAll();
            }
        });
    }

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(final String configName) {
        ChatModelConfig chat = correspondingConfig(configName).chatModel();
        if (!enabled(configName, "chat-model", chat.model())) {
            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    return new DisabledChatModel();
                }
            };
        }
        return new Function<>() {
            @Override
            public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                return getOrCreateChatModel(configName, context);
            }
        };
    }

    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(
            final String configName) {
        ChatModelConfig chat = correspondingConfig(configName).chatModel();
        if (!enabled(configName, "chat-model", chat.model())) {
            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    return new DisabledStreamingChatModel();
                }
            };
        }
        return new Function<>() {
            @Override
            public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                return getOrCreateChatModel(configName, context).streaming();
            }
        };
    }

    public Supplier<EmbeddingModel> embeddingModel(final String configName) {
        EmbeddingModelConfig emb = correspondingConfig(configName).embeddingModel();
        if (!enabled(configName, "embedding-model", emb.model())) {
            return new Supplier<EmbeddingModel>() {
                @Override
                public EmbeddingModel get() {
                    return new DisabledEmbeddingModel();
                }
            };
        }
        return new Supplier<EmbeddingModel>() {
            @Override
            public EmbeddingModel get() {
                JinferEmbeddingModel.Builder builder = JinferEmbeddingModel.builder();
                String modelRef = emb.model().get();
                if (isRemote(modelRef)) {
                    builder.model(modelRef);
                } else {
                    builder.modelPath(Path.of(modelRef));
                }
                if (emb.contextCapacity().isPresent()) {
                    builder.contextCapacity(emb.contextCapacity().getAsInt());
                }
                for (Map.Entry<String, String> companion : emb.companions().entrySet()) {
                    if (isRemote(companion.getValue())) {
                        builder.companion(companion.getKey(), companion.getValue());
                    } else {
                        builder.companionPath(companion.getKey(), Path.of(companion.getValue()));
                    }
                }
                if (emb.dimensions().isPresent()) {
                    builder.dimensions(emb.dimensions().getAsInt());
                }
                JinferEmbeddingModel model = builder.build();
                JinferModels.registerForClose(model);
                return model;
            }
        };
    }

    public Supplier<ScoringModel> scoringModel(final String configName) {
        ScoringModelConfig scoring = correspondingConfig(configName).scoringModel();
        if (!enabled(configName, "scoring-model", scoring.model())) {
            return new Supplier<ScoringModel>() {
                @Override
                public ScoringModel get() {
                    return new DisabledScoringModel();
                }
            };
        }
        return new Supplier<ScoringModel>() {
            @Override
            public ScoringModel get() {
                JinferScoringModel.Builder builder = JinferScoringModel.builder();
                String modelRef = scoring.model().get();
                if (isRemote(modelRef)) {
                    builder.model(modelRef);
                } else {
                    builder.modelPath(Path.of(modelRef));
                }
                if (scoring.contextCapacity().isPresent()) {
                    builder.contextCapacity(scoring.contextCapacity().getAsInt());
                }
                if (scoring.instruction().isPresent()) {
                    builder.instruction(scoring.instruction().get());
                }
                JinferScoringModel model = builder.build();
                JinferModels.registerForClose(model);
                return model;
            }
        };
    }

    public Function<SyntheticCreationalContext<DecisionModel>, DecisionModel> decisionModel(final String configName) {
        DecisionModelConfig decision = correspondingConfig(configName).decisionModel();
        if (!enabled(configName, "decision-model", decision.model())) {
            return new Function<>() {
                @Override
                public DecisionModel apply(SyntheticCreationalContext<DecisionModel> context) {
                    return new DisabledDecisionModel();
                }
            };
        }
        return new Function<>() {
            @Override
            public DecisionModel apply(SyntheticCreationalContext<DecisionModel> context) {
                JinferDecisionModel.Builder builder = JinferDecisionModel.builder();
                String modelRef = decision.model().get();
                if (isRemote(modelRef)) {
                    builder.model(modelRef);
                } else {
                    builder.modelPath(Path.of(modelRef));
                }
                if (decision.contextCapacity().isPresent()) {
                    builder.contextCapacity(decision.contextCapacity().getAsInt());
                }
                for (Map.Entry<String, String> companion : decision.companions().entrySet()) {
                    if (isRemote(companion.getValue())) {
                        builder.companion(companion.getKey(), companion.getValue());
                    } else {
                        builder.companionPath(companion.getKey(), Path.of(companion.getValue()));
                    }
                }
                List<DecisionModelListener> listeners = new ArrayList<>();
                for (DecisionModelListener listener : context.getInjectedReference(DECISION_MODEL_LISTENER_TYPE_LITERAL)) {
                    listeners.add(listener);
                }
                builder.listeners(listeners);
                ModelBuilderCustomizer.applyCustomizers(
                        context.getInjectedReference(DECISION_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                        builder, configName);
                JinferDecisionModel model = builder.build();
                JinferModels.registerForClose(model);
                return new WorkerPoolDecisionModel(model);
            }
        };
    }

    public Supplier<AudioTranscriptionModel> transcriptionModel(final String configName) {
        TranscriptionModelConfig transcription = correspondingConfig(configName).audioTranscriptionModel();
        if (!enabled(configName, "audio-transcription-model", transcription.model())) {
            return new Supplier<AudioTranscriptionModel>() {
                @Override
                public AudioTranscriptionModel get() {
                    return new DisabledTranscriptionModel();
                }
            };
        }
        return new Supplier<AudioTranscriptionModel>() {
            @Override
            public AudioTranscriptionModel get() {
                JinferTranscriptionModel.Builder builder = JinferTranscriptionModel.builder();
                String modelRef = transcription.model().get();
                if (isRemote(modelRef)) {
                    builder.model(modelRef);
                } else {
                    builder.modelPath(Path.of(modelRef));
                }
                JinferTranscriptionModel model = builder.build();
                JinferModels.registerForClose(model);
                return model;
            }
        };
    }

    public Supplier<TextToSpeechModel> ttsModel(final String configName) {
        TtsModelConfig tts = correspondingConfig(configName).ttsModel();
        if (!enabled(configName, "tts-model", tts.model())) {
            return new Supplier<TextToSpeechModel>() {
                @Override
                public TextToSpeechModel get() {
                    return new DisabledTtsModel();
                }
            };
        }
        return new Supplier<TextToSpeechModel>() {
            @Override
            public TextToSpeechModel get() {
                JinferSpeechModel.Builder builder = JinferSpeechModel.builder();
                String modelRef = tts.model().get();
                if (isRemote(modelRef)) {
                    builder.model(modelRef);
                } else {
                    builder.modelPath(Path.of(modelRef));
                }
                for (Map.Entry<String, String> companion : tts.companions().entrySet()) {
                    if (isRemote(companion.getValue())) {
                        builder.companion(companion.getKey(), companion.getValue());
                    } else {
                        builder.companionPath(companion.getKey(), Path.of(companion.getValue()));
                    }
                }
                if (tts.speed().isPresent()) {
                    builder.speed(tts.speed().getAsDouble());
                }
                if (tts.maxInputChars().isPresent()) {
                    builder.maxInputChars(tts.maxInputChars().getAsInt());
                }
                JinferSpeechModel model = builder.build();
                JinferModels.registerForClose(model);
                return model;
            }
        };
    }

    private JinferChatModel getOrCreateChatModel(String configName, SyntheticCreationalContext<?> context) {
        // shortcut: first loads of different chat models run one at a time, use per-config locks if that hurts
        synchronized (CHAT_LOAD_LOCK) {
            JinferChatModel model = JinferModels.getChatModel(configName);
            if (model == null) {
                model = buildChatModel(configName, context);
                JinferModels.storeChatModel(configName, model);
            }
            return model;
        }
    }

    private JinferChatModel buildChatModel(String configName, SyntheticCreationalContext<?> context) {
        LangChain4jJinferConfig.JinferConfig config = correspondingConfig(configName);
        ChatModelConfig chat = config.chatModel();
        JinferChatModel.Builder builder = JinferChatModel.builder();
        String modelRef = chat.model().get();
        if (isRemote(modelRef)) {
            builder.model(modelRef);
        } else {
            builder.modelPath(Path.of(modelRef));
        }
        if (chat.contextCapacity().isPresent()) {
            builder.contextCapacity(chat.contextCapacity().getAsInt());
        }
        if (chat.temperature().isPresent()) {
            builder.temperature(chat.temperature().getAsDouble());
        }
        if (chat.topP().isPresent()) {
            builder.topP(chat.topP().getAsDouble());
        }
        if (chat.topK().isPresent()) {
            builder.topK(chat.topK().getAsInt());
        }
        if (chat.minP().isPresent()) {
            builder.minP(chat.minP().getAsDouble());
        }
        if (chat.maxOutputTokens().isPresent()) {
            builder.maxOutputTokens(chat.maxOutputTokens().getAsInt());
        }
        if (chat.maxReasoningTokens().isPresent()) {
            builder.maxReasoningTokens(chat.maxReasoningTokens().getAsInt());
        }
        if (chat.seed().isPresent()) {
            builder.seed(chat.seed().getAsLong());
        }
        if (chat.promptCache().isPresent()) {
            builder.promptCache(chat.promptCache().get());
        }
        if (chat.thinking().isPresent()) {
            builder.thinking(chat.thinking().get());
        }
        for (Map.Entry<String, String> companion : chat.companions().entrySet()) {
            if (isRemote(companion.getValue())) {
                builder.companion(companion.getKey(), companion.getValue());
            } else {
                builder.companionPath(companion.getKey(), Path.of(companion.getValue()));
            }
        }
        if (config.timeout().isPresent()) {
            builder.timeout(config.timeout().get());
        }
        List<ChatModelListener> listeners = new ArrayList<>();
        for (ChatModelListener listener : context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL)) {
            listeners.add(listener);
        }
        builder.listeners(listeners);
        ModelBuilderCustomizer.applyCustomizers(
                context.getInjectedReference(CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                builder, configName);
        return builder.build();
    }

    private LangChain4jJinferConfig.JinferConfig correspondingConfig(String configName) {
        return NamedConfigUtil.isDefault(configName)
                ? runtimeConfig.getValue().defaultConfig()
                : runtimeConfig.getValue().namedConfig().get(configName);
    }

    /** Whether the integration is enabled for this config, which then must name a model and have the Vector API. */
    private boolean enabled(String configName, String typeKey, Optional<String> model) {
        if (!correspondingConfig(configName).enableIntegration()) {
            return false;
        }
        if (model.isEmpty()) {
            String key = "quarkus.langchain4j.jinfer." + qualify(configName, typeKey);
            throw new ConfigValidationException(new ConfigValidationException.Problem[] {
                    new ConfigValidationException.Problem(
                            "Jinfer " + typeKey + " has no model. Add a model reference or local GGUF path to "
                                    + "application.properties, for example:\n" + exampleConfig(key, typeKey))
            });
        }
        requireVectorApi();
        return true;
    }

    private static void requireVectorApi() {
        if (!ImageMode.current().isNativeImage() && ModuleLayer.boot().findModule("jdk.incubator.vector").isEmpty()) {
            throw new ConfigurationException(
                    "jinfer needs the Vector API: start the JVM with --add-modules jdk.incubator.vector, see "
                            + "https://docs.quarkiverse.io/quarkus-langchain4j/dev/jinfer.html#_vector_api");
        }
    }

    private static String exampleConfig(String key, String typeKey) {
        return switch (typeKey) {
            case "chat-model" -> key + ".model=LiquidAI/LFM2.5-1.2B-Instruct-GGUF:Q8_0";
            case "embedding-model" -> key + ".model=unsloth/embeddinggemma-2-GGUF:Q8_0";
            case "scoring-model" -> key + ".model=mradermacher/Qwen3-Reranker-0.6B-GGUF:Q8_0";
            case "decision-model" -> key + ".model=LiquidAI/d1-2B-GGUF:Q8_0";
            case "audio-transcription-model" -> key + ".model=mudler/parakeet-cpp-gguf/tdt-0.6b-v3-q8_0.gguf";
            case "tts-model" -> key + ".model=simonfxr/kokoro.cpp-GGUF:Q8_0\n"
                    + key + ".companions.voice=simonfxr/kokoro.cpp-GGUF/voices/kokoro-voice-af_heart.gguf";
            default -> throw new IllegalArgumentException(typeKey);
        };
    }

    // A URL goes to model(String) too, whose error names the reference to use instead. References resolve in the
    // builder's build(), after customizers, which may replace the model.
    private static boolean isRemote(String model) {
        return ModelStore.isRef(model) || model.contains("://");
    }

    private static String qualify(String configName, String typeKey) {
        return NamedConfigUtil.isDefault(configName) ? typeKey : "\"" + configName + "\"." + typeKey;
    }

    private static final class DisabledScoringModel implements ScoringModel {
        @Override
        public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
            throw new ModelDisabledException("ScoringModel is disabled");
        }
    }

    private static final class DisabledDecisionModel implements DecisionModel {
        @Override
        public DecisionResponse doDecide(DecisionRequest request) {
            throw disabled();
        }

        @Override
        public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
            return CompletableFuture.failedFuture(disabled());
        }

        private static ModelDisabledException disabled() {
            return new ModelDisabledException("DecisionModel is disabled");
        }
    }

    // shortcut: jinfer 0.4.0 has no doDecideAsync, remove once it does
    private static final class WorkerPoolDecisionModel implements DecisionModel {
        private final JinferDecisionModel delegate;

        WorkerPoolDecisionModel(JinferDecisionModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public DecisionResponse doDecide(DecisionRequest request) {
            return delegate.doDecide(request);
        }

        @Override
        public CompletableFuture<DecisionResponse> doDecideAsync(final DecisionRequest request) {
            return CompletableFuture.supplyAsync(new Supplier<>() {
                @Override
                public DecisionResponse get() {
                    return delegate.doDecide(request);
                }
            }, Infrastructure.getDefaultWorkerPool());
        }

        @Override
        public DecisionRequestParameters defaultRequestParameters() {
            return delegate.defaultRequestParameters();
        }

        @Override
        public List<DecisionModelListener> listeners() {
            return delegate.listeners();
        }

        @Override
        public ModelProvider provider() {
            return delegate.provider();
        }
    }

    private static final class DisabledTranscriptionModel implements AudioTranscriptionModel {
        @Override
        public AudioTranscriptionResponse transcribe(AudioTranscriptionRequest request) {
            throw new ModelDisabledException("AudioTranscriptionModel is disabled");
        }
    }

    private static final class DisabledTtsModel implements TextToSpeechModel {
        @Override
        public TextToSpeechResponse synthesize(TextToSpeechRequest request) {
            throw new ModelDisabledException("TextToSpeechModel is disabled");
        }
    }
}
