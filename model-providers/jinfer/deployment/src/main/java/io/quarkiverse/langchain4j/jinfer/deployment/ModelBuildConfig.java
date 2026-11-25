package io.quarkiverse.langchain4j.jinfer.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ModelBuildConfig {

    /**
     * Whether this model type should be enabled.
     */
    @ConfigDocDefault("true")
    Optional<Boolean> enabled();
}
