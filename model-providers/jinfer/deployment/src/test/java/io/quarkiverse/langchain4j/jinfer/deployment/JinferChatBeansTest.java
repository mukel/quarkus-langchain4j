package io.quarkiverse.langchain4j.jinfer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.qxotic.jinfer.langchain4j.JinferChatModel;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class JinferChatBeansTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FailingCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.enable-integration", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.custom.chat-model.model", "unused/repo:Q8_0");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    @ModelName("custom")
    ChatModel customChatModel;

    @Inject
    @ModelName("custom")
    StreamingChatModel customStreamingChatModel;

    @Test
    void chatBeanIsProducedAndDisabled() {
        assertThat(ClientProxy.unwrap(chatModel)).isInstanceOf(DisabledChatModel.class);
        assertThatExceptionOfType(ModelDisabledException.class)
                .isThrownBy(() -> chatModel.chat("Hello"));
    }

    @Test
    void streamingChatBeanIsProducedAndDisabled() {
        assertThat(ClientProxy.unwrap(streamingChatModel)).isInstanceOf(DisabledStreamingChatModel.class);
    }

    @Test
    void namedChatBeansApplyTheirCustomizerBeforeLoadingTheModel() {
        assertThatThrownBy(() -> customChatModel.chat("Hello")).hasRootCauseMessage("customized");
        assertThatThrownBy(() -> ClientProxy.unwrap(customStreamingChatModel)).hasRootCauseMessage("customized");
    }

    @ApplicationScoped
    @ModelName("custom")
    public static class FailingCustomizer implements ModelBuilderCustomizer<JinferChatModel.Builder> {
        @Override
        public void customize(JinferChatModel.Builder builder) {
            throw new IllegalStateException("customized");
        }
    }
}
