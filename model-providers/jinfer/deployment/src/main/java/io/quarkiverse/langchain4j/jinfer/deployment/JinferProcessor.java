package io.quarkiverse.langchain4j.jinfer.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.AUDIO_TRANSCRIPTION_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.DECISION_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.SCORING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.deployment.items.AudioTranscriptionModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.DecisionModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ScoringModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedAudioTranscriptionModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedDecisionModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedScoringModelProviderBuildItem;
import io.quarkiverse.langchain4j.jinfer.runtime.JinferRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ModuleEnableNativeAccessBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;

public class JinferProcessor {

    private static final String FEATURE = "langchain4j-jinfer";
    private static final String PROVIDER = "jinfer";

    // Automatic-Module-Name of the jinfer integration jar; used to request native access.
    private static final String JINFER_MODULE = "com.qxotic.jinfer.langchain4j";

    private static final List<String> JINFER_SERVICES = List.of(
            "com.qxotic.jam.JAM$Provider",
            "com.qxotic.jinfer.chat.ModelProvider",
            "com.qxotic.jinfer.fetch.Transport",
            "com.qxotic.jinfer.hub.Console");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Writes {@code Enable-Native-Access} into the production jar manifest. The Vector API flag cannot
     * be expressed there, so the recorder checks it at startup.
     */
    @BuildStep
    ModuleEnableNativeAccessBuildItem enableNativeAccess() {
        return new ModuleEnableNativeAccessBuildItem(JINFER_MODULE);
    }

    /**
     * Native images only keep registered service providers. Without these, jinfer silently loses its
     * fast kernels.
     */
    @BuildStep
    void registerServiceProviders(BuildProducer<ServiceProviderBuildItem> serviceProviders) {
        for (String service : JINFER_SERVICES) {
            serviceProviders.produce(ServiceProviderBuildItem.allProvidersFromClassPath(service));
        }
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            BuildProducer<EmbeddingModelProviderCandidateBuildItem> embeddingProducer,
            BuildProducer<ScoringModelProviderCandidateBuildItem> scoringProducer,
            BuildProducer<DecisionModelProviderCandidateBuildItem> decisionProducer,
            BuildProducer<AudioTranscriptionModelProviderCandidateBuildItem> transcriptionProducer,
            LangChain4jJinferBuildConfig config) {
        if (config.chatModel().enabled().orElse(true)) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.embeddingModel().enabled().orElse(true)) {
            embeddingProducer.produce(new EmbeddingModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.scoringModel().enabled().orElse(true)) {
            scoringProducer.produce(new ScoringModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.decisionModel().enabled().orElse(true)) {
            decisionProducer.produce(new DecisionModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.audioTranscriptionModel().enabled().orElse(true)) {
            transcriptionProducer.produce(new AudioTranscriptionModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void closeModelsAtShutdown(JinferRecorder recorder, ShutdownContextBuildItem shutdown) {
        recorder.closeModelsAtShutdown(shutdown);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateChatBeans(JinferRecorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChatModels,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        for (var selected : selectedChatModels) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                beanProducer.produce(chatBean(CHAT_MODEL, configName).createWith(recorder.chatModel(configName)).done());
                beanProducer.produce(chatBean(STREAMING_CHAT_MODEL, configName)
                        .createWith(recorder.streamingChatModel(configName))
                        .done());
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateEmbeddingBeans(JinferRecorder recorder,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbeddingModels,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        for (var selected : selectedEmbeddingModels) {
            if (PROVIDER.equals(selected.getProvider())) {
                beanProducer.produce(modelBean(EMBEDDING_MODEL, selected.getConfigName())
                        .unremovable()
                        .supplier(recorder.embeddingModel(selected.getConfigName()))
                        .done());
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateScoringBeans(JinferRecorder recorder,
            List<SelectedScoringModelProviderBuildItem> selectedScoringModels,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        for (var selected : selectedScoringModels) {
            if (PROVIDER.equals(selected.getProvider())) {
                beanProducer.produce(modelBean(SCORING_MODEL, selected.getConfigName())
                        .unremovable()
                        .supplier(recorder.scoringModel(selected.getConfigName()))
                        .done());
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateDecisionBeans(JinferRecorder recorder,
            List<SelectedDecisionModelProviderBuildItem> selectedDecisionModels,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        for (var selected : selectedDecisionModels) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                beanProducer.produce(modelBean(DECISION_MODEL, configName)
                        .unremovable()
                        .addInjectionPoint(instanceOf(ClassType.create(DotNames.DECISION_MODEL_LISTENER)))
                        .addInjectionPoint(customizers(JinferDotNames.DECISION_MODEL_BUILDER),
                                AnnotationInstance.builder(Any.class).build())
                        .createWith(recorder.decisionModel(configName))
                        .done());
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateTranscriptionBeans(JinferRecorder recorder,
            List<SelectedAudioTranscriptionModelProviderBuildItem> selectedTranscriptionModels,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        for (var selected : selectedTranscriptionModels) {
            if (PROVIDER.equals(selected.getProvider())) {
                beanProducer.produce(modelBean(AUDIO_TRANSCRIPTION_MODEL, selected.getConfigName())
                        .unremovable()
                        .supplier(recorder.transcriptionModel(selected.getConfigName()))
                        .done());
            }
        }
    }

    /**
     * TextToSpeechModel has no provider SPI in core, so jinfer produces the beans itself, one per
     * config name that is actually injected (default plus any {@code @ModelName}-qualified injection
     * point), mirroring how the core framework discovers named models.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateTtsBeans(JinferRecorder recorder,
            BeanDiscoveryFinishedBuildItem beanDiscovery,
            LangChain4jJinferBuildConfig config,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        if (!config.ttsModel().enabled().orElse(true)) {
            return;
        }
        Set<String> ttsNames = new LinkedHashSet<>();
        for (InjectionPointInfo ip : beanDiscovery.getInjectionPoints()) {
            if (!JinferDotNames.TEXT_TO_SPEECH_MODEL.equals(ip.getRequiredType().name())) {
                continue;
            }
            String name = determineModelName(ip);
            if (name != null) {
                ttsNames.add(name);
            }
        }
        for (String configName : ttsNames) {
            beanProducer.produce(modelBean(JinferDotNames.TEXT_TO_SPEECH_MODEL, configName)
                    .unremovable()
                    .supplier(recorder.ttsModel(configName))
                    .done());
        }
    }

    private static SyntheticBeanBuildItem.ExtendedBeanConfigurator chatBean(DotName type, String configName) {
        return modelBean(type, configName)
                .addInjectionPoint(instanceOf(ClassType.create(DotNames.CHAT_MODEL_LISTENER)))
                .addInjectionPoint(customizers(JinferDotNames.CHAT_MODEL_BUILDER),
                        AnnotationInstance.builder(Any.class).build());
    }

    private static SyntheticBeanBuildItem.ExtendedBeanConfigurator modelBean(DotName type, String configName) {
        var bean = SyntheticBeanBuildItem.configure(type)
                .setRuntimeInit()
                .defaultBean()
                .scope(ApplicationScoped.class);
        if (!NamedConfigUtil.isDefault(configName)) {
            bean.addQualifier(AnnotationInstance.builder(ModelName.class).add("value", configName).build());
        }
        return bean;
    }

    private static Type customizers(DotName builder) {
        return instanceOf(ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                new Type[] { ClassType.create(builder) }, null));
    }

    private static Type instanceOf(Type type) {
        return ParameterizedType.create(DotNames.CDI_INSTANCE, new Type[] { type }, null);
    }

    private static String determineModelName(InjectionPointInfo ip) {
        AnnotationInstance modelNameInstance = ip.getRequiredQualifier(JinferDotNames.MODEL_NAME);
        if (modelNameInstance != null) {
            AnnotationValue value = modelNameInstance.value();
            if (value != null && !value.asString().isEmpty()) {
                return value.asString();
            }
        }
        if (modelNameInstance == null && ip.isProgrammaticLookup()) {
            return null;
        }
        return NamedConfigUtil.DEFAULT_NAME;
    }
}
