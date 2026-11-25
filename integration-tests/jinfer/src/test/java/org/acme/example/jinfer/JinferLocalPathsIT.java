package org.acme.example.jinfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.qxotic.jinfer.hub.ModelStore;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.TextToSpeechModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Loads a model and its companion from relative paths. Its own profile keeps the downloads it needs out of
 * {@link JinferRealModelsIT}. Run it like that class, with {@code -Dit.test=JinferLocalPathsIT}.
 */
@QuarkusTest
@TestProfile(JinferLocalPathsIT.LocalPaths.class)
class JinferLocalPathsIT {

    public static class LocalPaths implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // only this named config is enabled, so the default models the application injects need none
            return Map.of(
                    "quarkus.langchain4j.jinfer.local.enable-integration", "true",
                    "quarkus.langchain4j.jinfer.local.tts-model.model",
                    localFile("simonfxr/kokoro.cpp-GGUF:Q8_0", "./target/kokoro.gguf"),
                    "quarkus.langchain4j.jinfer.local.tts-model.companions.voice",
                    localFile("simonfxr/kokoro.cpp-GGUF/voices/kokoro-voice-af_heart.gguf", "./target/kokoro-voice.gguf"),
                    "quarkus.langchain4j.jinfer.local.audio-transcription-model.model",
                    "mudler/parakeet-cpp-gguf/tdt-0.6b-v3-q8_0.gguf");
        }
    }

    // Links a cached model to a relative path, to load it as a local file.
    private static String localFile(String ref, String path) {
        try {
            Path link = Path.of(path);
            Files.deleteIfExists(link);
            Files.createSymbolicLink(link, ModelStore.standard().resolve(ref).toAbsolutePath());
            return link.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Inject
    @ModelName("local")
    TextToSpeechModel ttsModel;

    @Inject
    @ModelName("local")
    AudioTranscriptionModel transcriptionModel;

    @Test
    void speechLoadsAModelAndVoiceFromRelativePaths() {
        Audio audio = ttsModel.synthesize("The quick brown fox jumps over the lazy dog.").audio();
        assertThat(transcriptionModel.transcribeToText(audio)).containsIgnoringCase("fox");
    }
}
