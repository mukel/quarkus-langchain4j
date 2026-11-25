package io.quarkiverse.langchain4j.jinfer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.audio.TextToSpeechModel;
import dev.langchain4j.model.audio.TextToSpeechRequest;
import dev.langchain4j.model.audio.TextToSpeechResponse;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class JinferOwnModelBeansTest {

    // The default chat and embedding models and the "mine" TTS model are the application's own and have no jinfer
    // model configured. jinfer must leave them alone and still produce the other names.
    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(OwnChat.class, OwnEmbedding.class, OwnTts.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.local.chat-model.model", "./target/missing-chat.gguf")
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.tts-model.model", "./target/missing-tts.gguf");

    @Inject
    ChatModel chatModel;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    @ModelName("mine")
    TextToSpeechModel ownTtsModel;

    @Inject
    @ModelName("local")
    ChatModel jinferChatModel;

    @Inject
    TextToSpeechModel jinferTtsModel;

    @Test
    void theApplicationsBeansAreUsed() {
        assertThat(ClientProxy.unwrap(chatModel)).isInstanceOf(OwnChat.class);
        assertThat(ClientProxy.unwrap(embeddingModel)).isInstanceOf(OwnEmbedding.class);
        assertThat(ClientProxy.unwrap(ownTtsModel)).isInstanceOf(OwnTts.class);
    }

    @Test
    void jinferStillServesTheOtherNames() {
        assertThatThrownBy(() -> jinferChatModel.chat("hi")).hasStackTraceContaining("missing-chat.gguf");
        assertThatThrownBy(() -> jinferTtsModel.synthesize("hi")).hasStackTraceContaining("missing-tts.gguf");
    }

    @ApplicationScoped
    public static class OwnChat implements ChatModel {
    }

    @ApplicationScoped
    public static class OwnEmbedding implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            throw new UnsupportedOperationException();
        }
    }

    @ApplicationScoped
    @ModelName("mine")
    public static class OwnTts implements TextToSpeechModel {
        @Override
        public TextToSpeechResponse synthesize(TextToSpeechRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
