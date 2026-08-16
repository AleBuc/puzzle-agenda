package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.infrastructure.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ActivityControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.create("http://localhost:" + port);
        jdbcTemplate.update("TRUNCATE TABLE time_block, activity, materialized_day, horizon_state");
    }

    private record ApiResponse(int status, Object body) {
    }

    private ApiResponse post(String uri, Object body) {
        return restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeBody(response, Map.class)));
    }

    private ApiResponse put(String uri, Object body) {
        return restClient.put().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeBody(response, Map.class)));
    }

    private ApiResponse getList(String uri) {
        return restClient.get().uri(uri)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeBody(response, List.class)));
    }

    private ApiResponse delete(String uri) {
        return restClient.method(HttpMethod.DELETE).uri(uri)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeBody(response, Map.class)));
    }

    private static <T> T safeBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response, Class<T> type)
            throws java.io.IOException {
        if (response.getStatusCode().value() == 204) {
            return null;
        }
        return response.bodyTo(type);
    }

    @Test
    void createsAnActivityAndItAppearsInTheUnplannedBacklog() {
        ApiResponse created = post("/api/activities",
                Map.of("name", "Grocery run", "estimatedDurationMinutes", 30, "priority", "MEDIUM", "category", "errands"));

        assertThat(created.status()).isEqualTo(201);
        Map<String, Object> body = (Map<String, Object>) created.body();
        assertThat(body).containsEntry("name", "Grocery run").containsEntry("status", "UNPLANNED");

        ApiResponse unplanned = getList("/api/activities?status=unplanned");
        assertThat((List<?>) unplanned.body()).hasSize(1);
    }

    @Test
    void editsAnActivity() {
        ApiResponse created = post("/api/activities",
                Map.of("name", "Old", "estimatedDurationMinutes", 15, "priority", "LOW", "category", ""));
        String id = (String) ((Map<String, Object>) created.body()).get("id");

        ApiResponse edited = put("/api/activities/" + id,
                Map.of("name", "New", "estimatedDurationMinutes", 45, "priority", "HIGH", "category", "leisure"));

        assertThat(edited.status()).isEqualTo(200);
        assertThat((Map<String, Object>) edited.body()).containsEntry("name", "New").containsEntry("priority", "HIGH");
    }

    @Test
    void editingANonexistentActivityReturns404() {
        ApiResponse response = put("/api/activities/" + UUID.randomUUID(),
                Map.of("name", "X", "estimatedDurationMinutes", 10, "priority", "LOW", "category", ""));

        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    void deletesAnUnplannedActivityWithoutConfirmation() {
        ApiResponse created = post("/api/activities",
                Map.of("name", "Errand", "estimatedDurationMinutes", 20, "priority", "LOW", "category", ""));
        String id = (String) ((Map<String, Object>) created.body()).get("id");

        ApiResponse deleted = delete("/api/activities/" + id);

        assertThat(deleted.status()).isEqualTo(204);
        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity", Integer.class);
        assertThat(rowCount).isZero();
    }

    @Test
    void deletingANonexistentActivityReturns404() {
        ApiResponse response = delete("/api/activities/" + UUID.randomUUID());

        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    void rejectsABlankNameWith400() {
        ApiResponse response = post("/api/activities",
                Map.of("name", "   ", "estimatedDurationMinutes", 10, "priority", "LOW", "category", ""));

        assertThat(response.status()).isEqualTo(400);
    }
}
