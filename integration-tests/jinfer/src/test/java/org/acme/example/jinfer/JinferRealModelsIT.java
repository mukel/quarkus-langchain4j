package org.acme.example.jinfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.qxotic.jinfer.hub.ModelStore;
import com.qxotic.jinfer.langchain4j.JinferChatModel;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.TextToSpeechModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.DecisionResult;
import dev.langchain4j.service.decision.Scale;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.RegisterDecisionService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Multi;

/**
 * Runs every model type on real checkpoints, which are downloaded on first use (about 6 GB):
 * {@code ./mvnw verify -f integration-tests/jinfer/pom.xml -DskipITs=false -Dit.test=JinferRealModelsIT}, after
 * installing the extension with {@code ./mvnw install -DskipTests -f model-providers/jinfer/pom.xml}.
 */
@QuarkusTest
@TestProfile(JinferRealModelsIT.RealModels.class)
class JinferRealModelsIT {

    private static final String CHAT_MODEL = "LiquidAI/LFM2.5-1.2B-Instruct-GGUF:Q8_0";
    private static final String VOICE = "simonfxr/kokoro.cpp-GGUF/voices/kokoro-voice-af_heart.gguf";

    private static final String SPAM = "Congratulations! You won a free cruise! Send your bank details now!";
    private static final String HAM = "Hi team, the meeting moved to 3pm tomorrow in room B.";

    public static class RealModels implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    // a global timeout is for remote providers, every test below fails if jinfer inherits it
                    Map.entry("quarkus.langchain4j.timeout", "1ms"),
                    Map.entry("quarkus.langchain4j.jinfer.enable-integration", "true"),
                    Map.entry("quarkus.langchain4j.jinfer.chat-model.model", CHAT_MODEL),
                    Map.entry("quarkus.langchain4j.jinfer.chat-model.temperature", "0"),
                    Map.entry("quarkus.langchain4j.jinfer.chat-model.thinking", "false"),
                    Map.entry("quarkus.langchain4j.jinfer.chat-model.max-output-tokens", "256"),
                    Map.entry("quarkus.langchain4j.jinfer.timed.chat-model.model", CHAT_MODEL),
                    Map.entry("quarkus.langchain4j.jinfer.timed.timeout", "1ms"),
                    Map.entry("quarkus.langchain4j.jinfer.race.chat-model.model", CHAT_MODEL),
                    Map.entry("quarkus.langchain4j.jinfer.race.chat-model.max-output-tokens", "8"),
                    Map.entry("quarkus.langchain4j.jinfer.local.tts-model.model", "simonfxr/kokoro.cpp-GGUF:Q8_0"),
                    Map.entry("quarkus.langchain4j.jinfer.local.tts-model.companions.voice", localVoice()),
                    Map.entry("quarkus.langchain4j.jinfer.embedding-model.model", "unsloth/embeddinggemma-2-GGUF:Q8_0"),
                    Map.entry("quarkus.langchain4j.jinfer.short.embedding-model.model",
                            "unsloth/embeddinggemma-2-GGUF:Q8_0"),
                    Map.entry("quarkus.langchain4j.jinfer.short.embedding-model.dimensions", "256"),
                    Map.entry("quarkus.langchain4j.jinfer.scoring-model.model",
                            "mradermacher/Qwen3-Reranker-0.6B-GGUF:Q8_0"),
                    Map.entry("quarkus.langchain4j.jinfer.decision-model.model", "LiquidAI/d1-2B-GGUF:Q8_0"),
                    Map.entry("quarkus.langchain4j.jinfer.d3.decision-model.model", "ggml-org/vllm-d3-edge-GGUF:Q8_0"),
                    Map.entry("quarkus.langchain4j.decision-service.\"StrictFilter\".threshold-by-model.\"d1-2B-Q8_0.gguf\"",
                            "1.0"),
                    Map.entry("quarkus.langchain4j.jinfer.transcription-model.model",
                            "mudler/parakeet-cpp-gguf/tdt-0.6b-v3-q8_0.gguf"),
                    Map.entry("quarkus.langchain4j.jinfer.tts-model.model", "simonfxr/kokoro.cpp-GGUF:Q8_0"),
                    Map.entry("quarkus.langchain4j.jinfer.tts-model.companions.voice", VOICE),
                    Map.entry("quarkus.langchain4j.jinfer.vision.chat-model.model", "LiquidAI/LFM2.5-VL-450M-GGUF:Q8_0"),
                    Map.entry("quarkus.langchain4j.jinfer.vision.chat-model.temperature", "0"),
                    Map.entry("quarkus.langchain4j.jinfer.vision.chat-model.companions.media",
                            "LiquidAI/LFM2.5-VL-450M-GGUF/mmproj-LFM2.5-VL-450m-Q8_0.gguf"),
                    Map.entry("quarkus.langchain4j.jinfer.vision.decision-model.model", "LiquidAI/d1-2B-GGUF:Q8_0"),
                    Map.entry("quarkus.langchain4j.jinfer.vision.decision-model.companions.media",
                            "LiquidAI/d1-2B-GGUF/mmproj-d1-2B-Q8_0.gguf"));
        }
    }

    // Links the cached voice to a relative path, to load it as a local file.
    private static String localVoice() {
        try {
            Path link = Path.of("./target/kokoro-voice.gguf");
            Files.deleteIfExists(link);
            Files.createSymbolicLink(link, ModelStore.standard().resolve(VOICE).toAbsolutePath());
            return link.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record City(String name, String country) {
    }

    @ApplicationScoped
    public static class Warehouse {
        private final AtomicInteger calls = new AtomicInteger();

        public int calls() {
            return calls.get();
        }

        @Tool("Returns how many units of an item are in stock")
        public int stock(String item) {
            calls.incrementAndGet();
            return 42;
        }
    }

    @ApplicationScoped
    public static class ChatListener implements ChatModelListener {
        private final AtomicInteger responses = new AtomicInteger();

        public int responses() {
            return responses.get();
        }

        @Override
        public void onResponse(ChatModelResponseContext context) {
            responses.incrementAndGet();
        }
    }

    @RegisterAiService
    public interface Assistant {
        String chat(@MemoryId String conversation, @dev.langchain4j.service.UserMessage String message);

        @dev.langchain4j.service.UserMessage("{question}")
        Multi<String> stream(@MemoryId String conversation, @V("question") String question);

        @dev.langchain4j.service.UserMessage("Extract the city and its country from: {text}")
        City extract(@MemoryId String conversation, @V("text") String text);

        @ToolBox(Warehouse.class)
        String withTools(@MemoryId String conversation, @dev.langchain4j.service.UserMessage String message);
    }

    public enum Team {
        BILLING,
        SUPPORT
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH
    }

    public record Triage(
            @Decide("Is this about a payment problem?") boolean payment,
            @Decide("Which team should handle this ticket?") Team team) {
    }

    @RegisterDecisionService
    public interface TicketDesk {
        @Decide("Is this message spam?")
        boolean isSpam(String message);

        @Decide("Which team should handle this ticket?")
        Team team(String ticket);

        @Decide("Which team should handle this ticket?")
        Choice<Team> route(String ticket);

        @Decide("How severe is this ticket?")
        Scale<Severity> severity(String ticket);

        Triage triage(String ticket);

        @Decide("Is this message spam?")
        CompletableFuture<DecisionResult<Boolean>> isSpamAsync(String message);
    }

    @RegisterDecisionService
    public interface StrictFilter {
        @Decide("Is this message spam?")
        boolean isSpam(String message);
    }

    @RegisterDecisionService(modelName = "d3")
    public interface D3Filter {
        @Decide("Is this message spam?")
        boolean isSpam(String message);
    }

    @Inject
    Assistant assistant;

    @Inject
    Warehouse warehouse;

    @Inject
    ChatListener chatListener;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    @ModelName("short")
    EmbeddingModel shortEmbeddingModel;

    @Inject
    ScoringModel scoringModel;

    @Inject
    DecisionModel decisionModel;

    @Inject
    TicketDesk ticketDesk;

    @Inject
    StrictFilter strictFilter;

    @Inject
    D3Filter d3Filter;

    @Inject
    @ModelName("vision")
    ChatModel visionChatModel;

    @Inject
    @ModelName("vision")
    DecisionModel visionDecisionModel;

    @Inject
    @ModelName("timed")
    ChatModel timedChatModel;

    @Inject
    @ModelName("timed")
    StreamingChatModel timedStreamingChatModel;

    @Inject
    @ModelName("race")
    ChatModel raceChatModel;

    @Inject
    @ModelName("race")
    StreamingChatModel raceStreamingChatModel;

    @Inject
    @ModelName("local")
    TextToSpeechModel localVoiceTtsModel;

    @Inject
    TextToSpeechModel ttsModel;

    @Inject
    AudioTranscriptionModel transcriptionModel;

    @Test
    void chatRemembersTheConversationAndNotifiesListeners() {
        int before = chatListener.responses();
        assistant.chat("memory", "My name is Ada.");
        assertThat(assistant.chat("memory", "What is my name?")).containsIgnoringCase("Ada");
        assertThat(chatListener.responses()).isEqualTo(before + 2);
    }

    @Test
    void chatStreams() {
        List<String> tokens = assistant.stream("stream", "Count from 1 to 5.")
                .collect().asList().await().atMost(Duration.ofMinutes(5));
        assertThat(tokens.size()).isGreaterThan(1);
        assertThat(String.join("", tokens)).contains("5");
    }

    @Test
    void chatTimeoutStopsBlockingAndStreamingReplies() throws Exception {
        assertThatThrownBy(() -> timedChatModel.chat("Write a long story.")).isInstanceOf(TimeoutException.class);

        CompletableFuture<Throwable> failure = new CompletableFuture<>();
        timedStreamingChatModel.chat("Write a long story.", new StreamingChatResponseHandler() {
            @Override
            public void onCompleteResponse(ChatResponse response) {
                failure.complete(null);
            }

            @Override
            public void onError(Throwable error) {
                failure.complete(error);
            }
        });
        assertThat(failure.get(5, TimeUnit.MINUTES)).isInstanceOf(TimeoutException.class);
    }

    @Test
    void unsetTemperatureUsesTheModelsRecommendation() {
        try (JinferChatModel plain = JinferChatModel.builder().model(CHAT_MODEL).build()) {
            assertThat(timedChatModel.defaultRequestParameters().temperature())
                    .isEqualTo(plain.defaultRequestParameters().temperature());
        }
    }

    @Test
    void blockingAndStreamingBeansCanLoadTheSameModelConcurrently() throws Exception {
        CompletableFuture<String> blocking = CompletableFuture.supplyAsync(new Supplier<>() {
            @Override
            public String get() {
                return raceChatModel.chat("Say hello.");
            }
        });
        CompletableFuture<ChatResponse> streaming = new CompletableFuture<>();
        raceStreamingChatModel.chat("Say hello.", new StreamingChatResponseHandler() {
            @Override
            public void onCompleteResponse(ChatResponse response) {
                streaming.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                streaming.completeExceptionally(error);
            }
        });
        assertThat(blocking.get(5, TimeUnit.MINUTES)).isNotBlank();
        assertThat(streaming.get(5, TimeUnit.MINUTES).aiMessage().text()).isNotBlank();
    }

    @Test
    void chatReturnsStructuredOutput() {
        City city = assistant.extract("structured", "I flew to Tokyo, the capital of Japan.");
        assertThat(city.name()).containsIgnoringCase("Tokyo");
        assertThat(city.country()).containsIgnoringCase("Japan");
    }

    @Test
    void chatCallsTools() {
        int before = warehouse.calls();
        String answer = assistant.withTools("tools", "How many units of apples are in stock? Use the tool.");
        assertThat(warehouse.calls()).isGreaterThan(before);
        assertThat(answer).contains("42");
    }

    @Test
    void embeddingsRankRelatedTextFirstAtBothWidths() {
        for (EmbeddingModel model : List.of(embeddingModel, shortEmbeddingModel)) {
            List<Embedding> vectors = model.embedAll(List.of(
                    TextSegment.from("A cat sleeps on the sofa"),
                    TextSegment.from("A kitten naps on the couch"),
                    TextSegment.from("Quarterly tax filing deadlines"))).content();
            assertThat(dot(vectors.get(0), vectors.get(1))).isGreaterThan(dot(vectors.get(0), vectors.get(2)));
        }
        assertThat(embeddingModel.dimension()).isEqualTo(768);
        assertThat(shortEmbeddingModel.dimension()).isEqualTo(256);
        assertThat(shortEmbeddingModel.embed("hello").content().dimension()).isEqualTo(256);
    }

    @Test
    void scoringRanksTheRelevantDocumentFirst() {
        List<Double> scores = scoringModel.scoreAll(List.of(
                TextSegment.from("Paris is the capital of France."),
                TextSegment.from("Bananas are rich in potassium.")), "What is the capital of France?").content();
        assertThat(scores.get(0)).isGreaterThan(scores.get(1));
    }

    @Test
    void decisionAnswersEveryQuestionType() {
        DecisionResponse response = decisionModel.decide(DecisionRequest.builder()
                .input("My payout failed three days ago and I am losing money.")
                .question("urgent", YesNoQuestion.from("Does this need attention today?"))
                .question("team", ChoiceQuestion.builder()
                        .text("Which team should handle this ticket?")
                        .option("billing", "Payments, payouts, invoices, refunds")
                        .option("kitchen", "Food and drinks in the office")
                        .build())
                .question("severity", ScaleQuestion.from("How severe is this ticket?", List.of("low", "medium", "high")))
                .build());
        assertThat(response.yesNo("urgent").probability()).isBetween(0.0, 1.0);
        assertThat(response.choice("team").value()).isEqualTo("billing");
        assertThat(response.scale("severity").mean()).isBetween(0.0, 2.0);
        assertThat(response.modelName()).isEqualTo("d1-2B-Q8_0.gguf");
        assertThat(response.tokenUsage().inputTokenCount()).isPositive();
    }

    @Test
    void decisionReadsFieldsAndMessages() {
        YesNoQuestion spam = YesNoQuestion.from("Is this message spam?");
        assertThat(decisionModel.decide(DecisionRequest.builder()
                .inputFields(Map.of("subject", "You won!", "body", SPAM))
                .question("spam", spam).build()).yesNo("spam").probability()).isGreaterThan(0.5);
        assertThat(decisionModel.decide(DecisionRequest.builder()
                .inputMessages(UserMessage.from(HAM))
                .question("spam", spam).build()).yesNo("spam").probability()).isLessThan(0.5);
    }

    @Test
    void decisionServiceMapsEveryReturnType() throws Exception {
        String ticket = "I was charged twice for my invoice and need a refund.";
        assertThat(ticketDesk.isSpam(SPAM)).isTrue();
        assertThat(ticketDesk.isSpam(HAM)).isFalse();
        assertThat(ticketDesk.team(ticket)).isEqualTo(Team.BILLING);
        assertThat(ticketDesk.route(ticket).value()).isEqualTo(Team.BILLING);
        assertThat(ticketDesk.severity(ticket).mostLikely()).isNotNull();
        assertThat(ticketDesk.triage(ticket).team()).isEqualTo(Team.BILLING);

        DecisionResult<Boolean> async = ticketDesk.isSpamAsync(SPAM).get(5, TimeUnit.MINUTES);
        assertThat(async.content()).isTrue();
        assertThat(async.modelName()).isEqualTo("d1-2B-Q8_0.gguf");
    }

    @Test
    void decisionThresholdIsKeyedByTheGgufFileName() {
        assertThat(ticketDesk.isSpam(SPAM)).isTrue();
        assertThat(strictFilter.isSpam(SPAM)).isFalse();
    }

    @Test
    void namedDecisionModelRunsD3() {
        assertThat(d3Filter.isSpam(SPAM)).isTrue();
        assertThat(d3Filter.isSpam(HAM)).isFalse();
    }

    @Test
    void speechRoundTripsThroughTranscription() {
        Audio audio = ttsModel.synthesize("The quick brown fox jumps over the lazy dog.").audio();
        assertThat(transcriptionModel.transcribeToText(audio)).containsIgnoringCase("fox");
    }

    @Test
    void speechLoadsAVoiceFromARelativePath() {
        Audio audio = localVoiceTtsModel.synthesize("The quick brown fox jumps over the lazy dog.").audio();
        assertThat(transcriptionModel.transcribeToText(audio)).containsIgnoringCase("fox");
    }

    @Test
    void chatAndDecisionSeeImagesThroughTheirCompanions() throws Exception {
        ImageContent red = redImage();
        String answer = visionChatModel.chat(UserMessage.from(
                TextContent.from("What color is this image? Answer in one word."), red)).aiMessage().text();
        assertThat(answer).containsIgnoringCase("red");

        DecisionResponse response = visionDecisionModel.decide(DecisionRequest.builder()
                .inputContents(TextContent.from("Look at the image."), red)
                .question("red", YesNoQuestion.from("Is the image red?"))
                .build());
        assertThat(response.yesNo("red").probability()).isGreaterThan(0.5);
    }

    private static ImageContent redImage() throws Exception {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 256, 256);
        graphics.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return ImageContent.from(Base64.getEncoder().encodeToString(png.toByteArray()), "image/png");
    }

    private static double dot(Embedding a, Embedding b) {
        float[] x = a.vector();
        float[] y = b.vector();
        double dot = 0;
        for (int i = 0; i < x.length; i++) {
            dot += x[i] * y[i];
        }
        return dot;
    }
}
