package io.quarkiverse.langchain4j.jinfer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkus.test.QuarkusExtensionTest;

public class JinferLoadStreamingAtStartupTest {

    // Only the streaming bean is injected, so the unused ChatModel bean must survive for load-at-startup to find it.
    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(UsesStreamingChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.load-at-startup", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.chat-model.model", "./target/missing-chat-model.gguf")
            .assertException(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable failure) {
                    assertThat(failure).hasStackTraceContaining("missing-chat-model.gguf");
                }
            });

    @Test
    void anUnloadableChatModelStopsStartup() {
        fail("The application should not start when its model cannot be loaded");
    }

    @ApplicationScoped
    public static class UsesStreamingChatModel {
        @Inject
        StreamingChatModel streamingChatModel;
    }
}
