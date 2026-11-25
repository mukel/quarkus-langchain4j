package io.quarkiverse.langchain4j.jinfer.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.decision.DecisionModel;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;

public class JinferDecisionModelDisabledTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.langchain4j.jinfer.decision-model.enabled", "false");

    @Test
    void noDecisionBeanIsProduced() {
        assertThat(Arc.container().listAll(DecisionModel.class)).isEmpty();
    }
}
