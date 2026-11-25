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

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.QuarkusExtensionTest;

public class JinferMissingModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(UsesChatModel.class))
            .assertException(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable failure) {
                    assertThat(failure).hasMessageContaining(
                            "quarkus.langchain4j.jinfer.chat-model.model=LiquidAI/LFM2.5-1.2B-Instruct-GGUF:Q8_0");
                }
            });

    @Test
    void startupFailsWithAConfigLineToPaste() {
        fail("The application should not start without a model");
    }

    @ApplicationScoped
    public static class UsesChatModel {
        @Inject
        ChatModel chatModel;
    }
}
