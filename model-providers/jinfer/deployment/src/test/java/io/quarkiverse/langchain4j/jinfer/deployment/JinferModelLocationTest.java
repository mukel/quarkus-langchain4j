package io.quarkiverse.langchain4j.jinfer.deployment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusExtensionTest;

public class JinferModelLocationTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.embedding-model.model", "./target/missing-model.gguf")
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.url.embedding-model.model",
                    "https://example.com/model.gguf");

    @Inject
    EmbeddingModel pathModel;

    @Inject
    @ModelName("url")
    EmbeddingModel urlModel;

    @Test
    void localPathIsNotTreatedAsAReference() {
        assertThatThrownBy(() -> pathModel.embed("text")).hasStackTraceContaining("missing-model.gguf")
                .hasStackTraceContaining("NoSuchFile");
    }

    @Test
    void urlIsRefusedWithAHelpfulMessage() {
        assertThatThrownBy(() -> urlModel.embed("text")).hasStackTraceContaining("is a URL, not a model reference");
    }
}
