package io.quarkiverse.langchain4j.jinfer.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import com.qxotic.jinfer.langchain4j.JinferChatModel;

/**
 * JVM-wide registry of loaded jinfer models.
 *
 * <p>
 * This state is {@code static} on purpose: Quarkus instantiates a fresh recorder instance per
 * {@code @Record} build step, so a registry kept in recorder instance fields would not be shared
 * between the bean-generating build steps (which create and register models) and the shutdown build
 * step (which closes them). Static state is the only thing all those recorder instances see.
 */
final class JinferModels {

    private static final Logger LOG = Logger.getLogger(JinferModels.class);

    // One chat model per config name, shared by the ChatModel, StreamingChatModel and
    // TokenCountEstimator beans so the (large) weights are loaded only once.
    private static final Map<String, JinferChatModel> SHARED_CHAT = new HashMap<>();

    // Every loaded model (incl. the shared chat models) registers here so it is closed on shutdown.
    private static final List<AutoCloseable> CLOSEABLES = new ArrayList<>();

    private JinferModels() {
    }

    /**
     * Returns the already-loaded chat model for this config name, or {@code null} if none is loaded.
     */
    static synchronized JinferChatModel getChatModel(String configName) {
        return SHARED_CHAT.get(configName);
    }

    static synchronized void storeChatModel(String configName, JinferChatModel model) {
        SHARED_CHAT.put(configName, model);
        registerForClose(model);
    }

    static synchronized void registerForClose(AutoCloseable closeable) {
        CLOSEABLES.add(closeable);
    }

    static synchronized void closeAll() {
        for (AutoCloseable closeable : CLOSEABLES) {
            closeQuietly(closeable);
        }
        CLOSEABLES.clear();
        SHARED_CHAT.clear();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            LOG.warn("Failed to close a jinfer model at shutdown", e);
        }
    }
}
