package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.query.Metadata;
import io.quarkus.test.QuarkusExtensionTest;

public class EasyRagInputTypeTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TypedEmbeddingModel.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.easy-rag.path=src/test/resources/ragdocuments"),
                            "application.properties"));

    @Inject
    TypedEmbeddingModel model;

    @Inject
    RetrievalAugmentor augmentor;

    @Test
    void documentsAndQueriesAreEmbeddedWithTheirInputTypes() {
        assertThat(model.inputTypes()).isNotEmpty().containsOnly(EmbeddingInputType.DOCUMENT);

        UserMessage question = UserMessage.from("When was Charlie born?");
        augmentor.augment(new AugmentationRequest(question, Metadata.from(question, "default", List.of())));

        assertThat(model.inputTypes()).last().isEqualTo(EmbeddingInputType.QUERY);
    }

    @Alternative
    @Priority(1)
    @ApplicationScoped
    public static class TypedEmbeddingModel implements EmbeddingModel {
        private final List<EmbeddingInputType> inputTypes = new CopyOnWriteArrayList<>();

        public List<EmbeddingInputType> inputTypes() {
            return inputTypes;
        }

        @Override
        public EmbeddingResponse doEmbed(EmbeddingRequest request) {
            inputTypes.add(request.inputType());
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < request.inputs().size(); i++) {
                embeddings.add(Embedding.from(new float[] { 1, 0, 0 }));
            }
            return EmbeddingResponse.builder().embeddings(embeddings).build();
        }

        @Override
        public Set<EmbeddingParameter<?>> supportedParameters() {
            return Set.of(EmbeddingRequestParameters.INPUT_TYPE);
        }

        @Override
        public int dimension() {
            return 3;
        }
    }
}
