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
class TimeBlockControllerIT {

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

    // status is a plain int (not HttpStatus) — the 4xx/5xx reason-phrase enum constants
    // are not stable identifiers to assert against (e.g. 422 is UNPROCESSABLE_CONTENT
    // here, having been UNPROCESSABLE_ENTITY in older Spring versions per RFC 9110).
    private record ApiResponse(int status, Map<String, Object> body) {
    }

    private ApiResponse post(String uri, Object body) {
        return restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body)
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

    private String createActivity(String name, int estimatedDurationMinutes) {
        ApiResponse created = post("/api/activities",
                Map.of("name", name, "estimatedDurationMinutes", estimatedDurationMinutes, "priority", "MEDIUM", "category", "sport"));
        return (String) created.body().get("id");
    }

    private String planFragment(String activityId, LocalDate day, String startTime, String endTime) {
        ApiResponse created = post("/api/days/" + day + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", startTime, "endTime", endTime, "activityId", activityId));
        return (String) created.body().get("id");
    }

    // --- tests ------------------------------------------------------------

    @Test
    void createsABlockAndItAppearsOnTheDay() {
        ApiResponse created = post(
                "/api/days/" + TODAY + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "09:00", "endTime", "10:30", "name", "Standup"));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body()).containsEntry("startTime", "09:00").containsEntry("endTime", "10:30");

        ApiResponse day = get("/api/days/" + TODAY);
        assertThat(day.status()).isEqualTo(200);
        assertThat((List<?>) day.body().get("blocks")).hasSize(1);
    }

    @Test
    void adjacentBlocksAreBothAccepted() {
        post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "09:00", "endTime", "10:30"));

        ApiResponse adjacent = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "10:30", "endTime", "11:00"));

        assertThat(adjacent.status()).isEqualTo(201);
    }

    @Test
    void overlappingBlockIsRejectedWith409AndTheExcludeConstraintHolds() {
        post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "09:00", "endTime", "10:00"));

        ApiResponse overlapping = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "09:30", "endTime", "09:45"));

        assertThat(overlapping.status()).isEqualTo(409);
        assertThat(overlapping.body()).containsEntry("reason", "TIME_BLOCK_OVERLAP");

        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM time_block", Integer.class);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void nonFiveMinuteGranularityIsRejectedWith400() {
        ApiResponse response = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "09:00", "endTime", "09:07"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).containsEntry("reason", "INVALID_TIME_GRANULARITY");
    }

    @Test
    void beyondTheForwardHorizonIsRejectedWith422() {
        LocalDate farDate = TODAY.plusDays(14);

        ApiResponse response = post("/api/days/" + farDate + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "09:00", "endTime", "10:00"));

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body()).containsEntry("reason", "DAY_BEYOND_FORWARD_HORIZON");
    }

    @Test
    void editsABlocksTimeAndReturns200() {
        ApiResponse created = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "09:00", "endTime", "10:00", "name", "Old"));
        String id = (String) created.body().get("id");

        ApiResponse edited = put("/api/blocks/" + id,
                Map.of("startTime", "09:15", "endTime", "10:15", "name", "New"));

        assertThat(edited.status()).isEqualTo(200);
        assertThat(edited.body()).containsEntry("startTime", "09:15").containsEntry("name", "New");
    }

    @Test
    void editingANonexistentBlockReturns404() {
        ApiResponse response = put("/api/blocks/" + UUID.randomUUID(),
                Map.of("startTime", "09:00", "endTime", "10:00"));

        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    void deletesABlockAndItNoLongerAppearsOnTheDay() {
        ApiResponse created = post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "09:00", "endTime", "10:00"));
        String id = (String) created.body().get("id");

        ApiResponse deleted = delete("/api/blocks/" + id);
        assertThat(deleted.status()).isEqualTo(204);

        ApiResponse day = get("/api/days/" + TODAY);
        assertThat((List<?>) day.body().get("blocks")).isEmpty();
    }

    @Test
    void deletingANonexistentBlockReturns404() {
        ApiResponse response = delete("/api/blocks/" + UUID.randomUUID());

        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    void midnightSpanningBlockAppearsOnBothTheStartDayAndTheSpilloverDay() {
        ApiResponse created = post(
                "/api/days/" + TODAY + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "23:00", "endTime", "07:00", "name", "Sleep"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body()).containsEntry("endsNextDay", true).containsEntry("startsPreviousDay", false);

        ApiResponse startDay = get("/api/days/" + TODAY);
        assertThat((List<?>) startDay.body().get("blocks")).hasSize(1);

        ApiResponse spilloverDay = get("/api/days/" + TODAY.plusDays(1));
        List<?> spilloverBlocks = (List<?>) spilloverDay.body().get("blocks");
        assertThat(spilloverBlocks).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> spilloverBlock = (Map<String, Object>) spilloverBlocks.get(0);
        assertThat(spilloverBlock)
                .containsEntry("startTime", "23:00")
                .containsEntry("endTime", "07:00")
                .containsEntry("startsPreviousDay", true)
                .containsEntry("endsNextDay", false);

        // A new block starting at 00:00 on the spillover day must still be rejected as overlapping.
        ApiResponse overlapping = post("/api/days/" + TODAY.plusDays(1) + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "00:00", "endTime", "01:00"));
        assertThat(overlapping.status()).isEqualTo(409);
    }

    @Test
    void dayBeforeDay1IsRejectedWith404() {
        // Establish Day 1 at TODAY via a first placement, then a day before it must 404.
        post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "09:00", "endTime", "10:00"));

        ApiResponse response = get("/api/days/" + TODAY.minusDays(1));

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body()).containsEntry("reason", "DAY_NOT_REACHABLE");
    }

    // --- US2: same-activity, same-day merge via edit -----------------------

    @Test
    void editingAFragmentToTouchAnotherSameActivityFragmentMergesThem() {
        String activityId = createActivity("Course a pied", 45);
        planFragment(activityId, TODAY, "07:00", "07:20");
        String second = planFragment(activityId, TODAY, "07:40", "08:00");

        ApiResponse edited = put("/api/blocks/" + second, Map.of("startTime", "07:20", "endTime", "08:00"));

        assertThat(edited.status()).isEqualTo(200);
        assertThat(edited.body()).containsEntry("startTime", "07:00").containsEntry("endTime", "08:00");

        ApiResponse day = get("/api/days/" + TODAY);
        assertThat((List<?>) day.body().get("blocks")).hasSize(1);
    }

    @Test
    void editingAFragmentBetweenTwoOthersMergesAllThreeInOneOperation() {
        String activityId = createActivity("Course a pied", 45);
        planFragment(activityId, TODAY, "07:00", "07:20");
        planFragment(activityId, TODAY, "07:40", "08:00");
        String middle = planFragment(activityId, TODAY, "07:25", "07:35");

        ApiResponse edited = put("/api/blocks/" + middle, Map.of("startTime", "07:20", "endTime", "07:40"));

        assertThat(edited.status()).isEqualTo(200);
        assertThat(edited.body()).containsEntry("startTime", "07:00").containsEntry("endTime", "08:00");

        ApiResponse day = get("/api/days/" + TODAY);
        assertThat((List<?>) day.body().get("blocks")).hasSize(1);
    }

    @Test
    void editOverlapWithADifferentActivitysFragmentIsStillRejected() {
        String activityId = createActivity("Course a pied", 45);
        String otherActivityId = createActivity("Reading", 30);
        String fragment = planFragment(activityId, TODAY, "09:00", "09:30");
        planFragment(otherActivityId, TODAY, "10:00", "10:30");

        ApiResponse response = put("/api/blocks/" + fragment, Map.of("startTime", "09:00", "endTime", "10:15"));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body()).containsEntry("reason", "TIME_BLOCK_OVERLAP");
    }

    // --- US4: fragment-scope deletion ---------------------------------------

    @Test
    void deletingTheOnlyFragmentOfADayNeedsNoScope() {
        String activityId = createActivity("Course a pied", 45);
        String fragment = planFragment(activityId, TODAY, "07:00", "07:30");

        ApiResponse response = delete("/api/blocks/" + fragment);

        assertThat(response.status()).isEqualTo(204);
    }

    @Test
    void deletingWithScopeSelfRemovesOnlyThatFragment() {
        String activityId = createActivity("Course a pied", 45);
        String first = planFragment(activityId, TODAY, "07:00", "07:20");
        planFragment(activityId, TODAY, "18:00", "18:25");

        ApiResponse response = delete("/api/blocks/" + first + "?scope=self");

        assertThat(response.status()).isEqualTo(204);
        ApiResponse day = get("/api/days/" + TODAY);
        assertThat((List<?>) day.body().get("blocks")).hasSize(1);
    }

    @Test
    void deletingWithScopeActivityDayRemovesEveryFragmentOfThatActivityThatDay() {
        String activityId = createActivity("Course a pied", 45);
        String first = planFragment(activityId, TODAY, "07:00", "07:20");
        planFragment(activityId, TODAY, "18:00", "18:25");
        planFragment(activityId, TODAY.plusDays(1), "07:00", "07:20");

        ApiResponse response = delete("/api/blocks/" + first + "?scope=activityDay");

        assertThat(response.status()).isEqualTo(204);
        ApiResponse day = get("/api/days/" + TODAY);
        assertThat((List<?>) day.body().get("blocks")).isEmpty();
        ApiResponse nextDay = get("/api/days/" + TODAY.plusDays(1));
        assertThat((List<?>) nextDay.body().get("blocks")).hasSize(1);
    }

    @Test
    void invalidScopeValueReturns400() {
        String activityId = createActivity("Course a pied", 45);
        String fragment = planFragment(activityId, TODAY, "07:00", "07:30");

        ApiResponse response = delete("/api/blocks/" + fragment + "?scope=bogus");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).containsEntry("reason", "INVALID_REQUEST");
    }
}
