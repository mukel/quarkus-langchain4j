package io.quarkiverse.langchain4j.jinfer.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A jinfer model bean that {@code load-at-startup} can create.
 */
final class JinferModelBuildItem extends MultiBuildItem {

    private final Class<?> type;
    private final String configName;

    JinferModelBuildItem(Class<?> type, String configName) {
        this.type = type;
        this.configName = configName;
    }

    Class<?> type() {
        return type;
    }

    String configName() {
        return configName;
    }
}
