### LangChain4j Jinfer

`Assistant.java` is an AI service answered by a small LLM that runs inside this application, with no server or API key.
The model is downloaded on first use into this machine's cache (about 1.2 GB), and later runs reuse it.
Change it with `quarkus.langchain4j.jinfer.chat-model.model` in `src/main/resources/application.properties`.

Dev mode and tests are ready to go. To run the packaged application, enable the Vector API:

```shell script
java --add-modules jdk.incubator.vector -jar target/quarkus-app/quarkus-run.jar
```

`src/main/docker/Dockerfile.jvm` already sets it. A new container downloads the model again, unless you [ship it in the image](https://docs.quarkiverse.io/quarkus-langchain4j/dev/jinfer.html#_models_in_the_container_image).
See the [jinfer guide](https://docs.quarkiverse.io/quarkus-langchain4j/dev/jinfer.html) for other model types and settings.
