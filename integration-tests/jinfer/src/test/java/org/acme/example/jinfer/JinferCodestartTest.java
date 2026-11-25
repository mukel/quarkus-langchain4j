package org.acme.example.jinfer;

import static io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog.Language.JAVA;
import static io.quarkus.devtools.testing.SnapshotTesting.checkContains;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;

public class JinferCodestartTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .setupStandaloneExtensionTest("io.quarkiverse.langchain4j:quarkus-langchain4j-jinfer")
            .languages(JAVA)
            .build();

    @Test
    void generatesAnAssistantWithAModelAndTheVectorApiFlag() throws Throwable {
        codestartTest.assertThatGeneratedFile(JAVA, "src/main/java/ilove/quark/us/Assistant.java")
                .satisfies(checkContains("@RegisterAiService"));
        codestartTest.assertThatGeneratedFile(JAVA, "src/main/resources/application.properties")
                .satisfies(checkContains("quarkus.langchain4j.jinfer.chat-model.model=LiquidAI/LFM2.5-1.2B-Instruct-GGUF:Q8_0"))
                .satisfies(checkContains("quarkus.test.arg-line=--add-modules=jdk.incubator.vector"));
        codestartTest.assertThatGeneratedFile(JAVA, "src/main/docker/Dockerfile.jvm")
                .satisfies(checkContains("FROM registry.access.redhat.com/ubi"))
                .content()
                .endsWith("ENV JAVA_OPTS_APPEND=\"$JAVA_OPTS_APPEND --add-modules=jdk.incubator.vector\"\n");
    }
}
