package org.acme;

import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Answered by the jinfer model configured in application.properties. Inject it where you need it:
 *
 * <pre>
 * &#64;Inject
 * Assistant assistant;
 * </pre>
 */
@RegisterAiService
public interface Assistant {

    String chat(String message);
}
