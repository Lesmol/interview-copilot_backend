package dev.interviewcopilot.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Keeps the committed contract {@code openapi/backend.json} in step with the code.
 * Regenerate with {@code ./mvnw test -Dopenapi.update=true}.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
@AutoConfigureMockMvc
class OpenApiContractTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JdbcClient jdbc;

    @Test
    void committedSpecMatchesGeneratedSpec() throws Exception {
        String generated = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        ObjectMapper mapper = new ObjectMapper();
        Path file = Path.of("openapi/backend.json");
        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(mapper.readTree(generated)) + "\n");
        }
        assertThat(mapper.readTree(Files.readString(file))).isEqualTo(mapper.readTree(generated));
    }
}
