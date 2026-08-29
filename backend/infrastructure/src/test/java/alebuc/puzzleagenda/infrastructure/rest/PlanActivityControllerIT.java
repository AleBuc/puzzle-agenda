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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PlanActivityControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final LocalDate TODAY = LocalDate.now();

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

    private record ApiResponse(int status, Map<String, Object> body) {
    }

    private ApiResponse post(String uri, Object body) {
        return restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeBody(response)));
    }

    private ApiResponse patch(String uri, Object body) {
        return restClient.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeBody(response)));
    }

    private ApiResponse put(String uri, Object body) {
        return restClient.put().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeBody(response)));
    }

    private ApiResponse get(String uri) {
        return restClient.get().uri(uri)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeBody(response)));
    }

    private ApiResponse delete(String uri) {
        return restClient.method(HttpMethod.DELETE).uri(uri)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeBody(response)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> safeBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws java.io.IOException {
        if (response.getStatusCode().value() == 204) {
            return null;
        }
        return (Map<String, Object>) response.bodyTo(Map.class);
    }

    private String createActivity(String name) {
        ApiResponse created = post("/api/activities",
                Map.of("name", name, "estimatedDurationMinutes", 30, "priority", "MEDIUM", "category", "errands"));
        return (String) created.body().get("id");
    }

    private String createActivity(String name, int estimatedDurationMinutes) {
        ApiResponse created = post("/api/activities",
                Map.of("name", name, "estimatedDurationMinutes", estimatedDurationMinutes, "priority", "MEDIUM", "category", "sport"));
        return (String) created.body().get("id");
    }

    @Test
    void planningAnActivityKeepsItInTheBacklogWithFragmentInfo() {
        String activityId = createActivity("Grocery run");

        ApiResponse created = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "14:00", "endTime", "15:00", "activityId", activityId));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body()).containsEntry("activityName", "Grocery run");

        ApiResponse day = get("/api/days/" + TODAY);
        assertThat((List<?>) day.body().get("blocks")).hasSize(1);

        // The activity still appears in the backlog (FR-012) — it is no longer removed
        // once planned; instead it now carries fragment/aggregate info (feature 002).
        String rawList = restClient.get().uri("/api/activities").retrieve().body(String.class);
        assertThat(rawList).contains(activityId).contains("\"totalFragmentCount\":1");
    }

    @Test
    void planningASecondFragmentOnADifferentDaySucceeds() {
        // FR-001 (feature 002): an activity may have concurrent fragments on several days.
        String activityId = createActivity("Write report", 300);
        post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "09:00", "endTime", "11:00", "activityId", activityId));

        ApiResponse secondFragment = post("/api/days/" + TODAY.plusDays(2) + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "09:00", "endTime", "11:00", "activityId", activityId));

        assertThat(secondFragment.status()).isEqualTo(201);
    }

    @Test
    void anAdditionalFragmentBeyondTheDailyQuotaIsStillAccepted() {
        // FR-004: over-allocation on a single day is never rejected.
        String activityId = createActivity("Write report", 60);
        post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "09:00", "endTime", "10:00", "activityId", activityId));

        ApiResponse overQuota = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "20:00", "endTime", "21:00", "activityId", activityId));

        assertThat(overQuota.status()).isEqualTo(201);
    }

    @Test
    void planningANonexistentActivityIsRejectedWith409() {
        ApiResponse response = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "14:00", "endTime", "15:00", "activityId", UUID.randomUUID().toString()));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body()).containsEntry("reason", "ACTIVITY_NOT_AVAILABLE");
    }

    @Test
    void deletingAPlannedActivityBlockReturnsTheActivityToUnplannedForThatDay() {
        String activityId = createActivity("Grocery run");
        ApiResponse created = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "14:00", "endTime", "15:00", "activityId", activityId));
        String blockId = (String) created.body().get("id");

        ApiResponse deleted = delete("/api/blocks/" + blockId);
        assertThat(deleted.status()).isEqualTo(204);

        String rawList = restClient.get().uri("/api/activities?day=" + TODAY).retrieve().body(String.class);
        assertThat(rawList).contains(activityId).contains("\"dayStatus\":\"UNPLANNED\"");
    }

    @Test
    void movesAPlannedActivityBlockToANewDayAndSlot() {
        String activityId = createActivity("Grocery run");
        ApiResponse created = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "14:00", "endTime", "15:00", "activityId", activityId));
        String blockId = (String) created.body().get("id");

        LocalDate tomorrow = TODAY.plusDays(1);
        ApiResponse moved = patch("/api/blocks/" + blockId + "/move",
                Map.of("day", tomorrow.toString(), "startTime", "10:00", "endTime", "11:00"));

        assertThat(moved.status()).isEqualTo(200);
        assertThat(moved.body()).containsEntry("startTime", "10:00");

        ApiResponse oldDay = get("/api/days/" + TODAY);
        assertThat((List<?>) oldDay.body().get("blocks")).isEmpty();
        ApiResponse newDay = get("/api/days/" + tomorrow);
        assertThat((List<?>) newDay.body().get("blocks")).hasSize(1);
    }

    @Test
    void movingABlockThatIsNotPlannedActivityIsRejectedWith400() {
        ApiResponse created = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "09:00", "endTime", "10:00"));
        String blockId = (String) created.body().get("id");

        ApiResponse response = patch("/api/blocks/" + blockId + "/move",
                Map.of("day", TODAY.toString(), "startTime", "11:00", "endTime", "12:00"));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void deletingAnActivityWithAPlannedFragmentWithoutConfirmationIsRejectedWith409() {
        String activityId = createActivity("Grocery run");
        post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "14:00", "endTime", "15:00", "activityId", activityId));

        ApiResponse response = delete("/api/activities/" + activityId);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body()).containsEntry("reason", "ACTIVITY_HAS_PLANNED_FRAGMENTS");
    }

    @Test
    void confirmedDeleteOfAPlannedActivityAlsoRemovesItsScheduledBlock() {
        String activityId = createActivity("Grocery run");
        ApiResponse created = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "14:00", "endTime", "15:00", "activityId", activityId));
        String blockId = (String) created.body().get("id");

        ApiResponse response = delete("/api/activities/" + activityId + "?confirm=true");
        assertThat(response.status()).isEqualTo(204);

        ApiResponse day = get("/api/days/" + TODAY);
        assertThat((List<?>) day.body().get("blocks")).isEmpty();

        Integer activityCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity", Integer.class);
        assertThat(activityCount).isZero();
        Integer blockCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM time_block WHERE id = ?::uuid", Integer.class, blockId);
        assertThat(blockCount).isZero();
    }

    // --- US2: same-activity, same-day merge --------------------------------

    @Test
    void creatingAFragmentAdjacentToAnExistingSameActivityFragmentMergesThem() {
        String activityId = createActivity("Course a pied", 45);
        post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "07:00", "endTime", "07:20", "activityId", activityId));
        post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "18:00", "endTime", "18:25", "activityId", activityId));

        ApiResponse merged = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "07:20", "endTime", "07:35", "activityId", activityId));

        assertThat(merged.status()).isEqualTo(201);
        assertThat(merged.body()).containsEntry("startTime", "07:00").containsEntry("endTime", "07:35");

        ApiResponse day = get("/api/days/" + TODAY);
        List<?> blocks = (List<?>) day.body().get("blocks");
        assertThat(blocks).hasSize(2);
    }

    @Test
    void midnightSpanningPlannedActivityIsRejectedWith400() {
        String activityId = createActivity("Sleep-ish activity");

        ApiResponse response = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", "23:00", "endTime", "07:00", "activityId", activityId));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).containsEntry("reason", "PLANNED_ACTIVITY_SPANS_MIDNIGHT");

        // The same times for a ROUTINE block are still accepted (FR-021 is PLANNED_ACTIVITY-only).
        ApiResponse routine = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "ROUTINE", "startTime", "23:00", "endTime", "07:00", "name", "Sleep"));
        assertThat(routine.status()).isEqualTo(201);
    }
}
