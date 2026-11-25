package io.quarkiverse.langchain4j.jinfer.deployment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.qxotic.jinfer.langchain4j.JinferDecisionModel;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusExtensionTest;

public class JinferDecisionBeanTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FailingCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.enable-integration", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.custom.decision-model.model", "unused/repo:Q8_0");

    private static final DecisionRequest REQUEST = DecisionRequest.builder()
            .input("Congratulations! You won a free cruise!")
            .question("spam", YesNoQuestion.from("Is this message spam?"))
            .build();

    @Inject
    DecisionModel decisionModel;

    @Inject
    @ModelName("custom")
    DecisionModel customDecisionModel;

    @Test
    void defaultDecisionBeanIsProducedAndDisabled() {
        assertThatThrownBy(() -> decisionModel.decide(REQUEST)).isInstanceOf(ModelDisabledException.class);
        assertThatThrownBy(() -> decisionModel.decideAsync(REQUEST).get())
                .hasRootCauseInstanceOf(ModelDisabledException.class);
    }

    @Test
    void namedDecisionBeanAppliesItsCustomizerBeforeLoadingTheModel() {
        assertThatThrownBy(() -> customDecisionModel.decide(REQUEST)).hasRootCauseMessage("customized");
    }

    @ApplicationScoped
    @ModelName("custom")
    public static class FailingCustomizer implements ModelBuilderCustomizer<JinferDecisionModel.Builder> {
        @Override
        public void customize(JinferDecisionModel.Builder builder) {
            throw new IllegalStateException("customized");
        }
    }
}
