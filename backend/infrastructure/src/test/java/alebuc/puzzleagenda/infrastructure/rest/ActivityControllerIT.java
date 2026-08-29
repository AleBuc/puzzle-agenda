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
class ActivityControllerIT {

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

    @SuppressWarnings("unchecked")
    private String createActivity(String name, int estimatedDurationMinutes) {
        ApiResponse created = post("/api/activities",
                Map.of("name", name, "estimatedDurationMinutes", estimatedDurationMinutes, "priority", "MEDIUM", "category", "errands"));
        return (String) ((Map<String, Object>) created.body()).get("id");
    }

    private void planFragment(String activityId, LocalDate day, String startTime, String endTime) {
        post("/api/days/" + day + "/blocks",
                Map.of("type", "PLANNED_ACTIVITY", "startTime", startTime, "endTime", endTime, "activityId", activityId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findInList(List<?> list, String id) {
        return list.stream()
                .map(entry -> (Map<String, Object>) entry)
                .filter(entry -> id.equals(entry.get("id")))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void createsAnActivityWithNoStatusFieldAndZeroFragments() {
        ApiResponse created = post("/api/activities",
                Map.of("name", "Grocery run", "estimatedDurationMinutes", 30, "priority", "MEDIUM", "category", "errands"));

        assertThat(created.status()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) created.body();
        assertThat(body).containsEntry("name", "Grocery run").doesNotContainKey("status");
        assertThat(body.get("totalFragmentCount")).isEqualTo(0);
        assertThat(body.get("plannedDayCount")).isEqualTo(0);

        ApiResponse list = getList("/api/activities");
        assertThat((List<?>) list.body()).hasSize(1);
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
    void deletesAnActivityWithNoFragmentsWithoutConfirmation() {
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

    // --- US1: day-scoped remaining time / status --------------------------

    @Test
    void dayScopedListReturnsIndependentRemainingTimePerDay() {
        String activityId = createActivity("Write report", 300);
        planFragment(activityId, TODAY, "09:00", "11:00");
        planFragment(activityId, TODAY.plusDays(2), "09:00", "11:00");

        Map<String, Object> today = findInList((List<?>) getList("/api/activities?day=" + TODAY).body(), activityId);
        assertThat(today.get("remainingMinutesForDay")).isEqualTo(180);
        assertThat(today.get("dayStatus")).isEqualTo("PARTIALLY_PLANNED");

        Map<String, Object> dayPlusTwo = findInList(
                (List<?>) getList("/api/activities?day=" + TODAY.plusDays(2)).body(), activityId);
        assertThat(dayPlusTwo.get("remainingMinutesForDay")).isEqualTo(180);
        assertThat(dayPlusTwo.get("dayStatus")).isEqualTo("PARTIALLY_PLANNED");

        Map<String, Object> emptyDay = findInList(
                (List<?>) getList("/api/activities?day=" + TODAY.plusDays(5)).body(), activityId);
        assertThat(emptyDay.get("remainingMinutesForDay")).isEqualTo(300);
        assertThat(emptyDay.get("dayStatus")).isEqualTo("UNPLANNED");
    }

    @Test
    void malformedDayQueryParamReturns400() {
        int status = restClient.get().uri("/api/activities?day=not-a-date")
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(400);
    }

    // --- US3: aggregate backlog view ---------------------------------------

    @Test
    void aggregateListReturnsFragmentCountAndSparsePerDayBreakdown() {
        String activityId = createActivity("Write report", 120);
        planFragment(activityId, TODAY, "09:00", "10:00");
        planFragment(activityId, TODAY.plusDays(2), "09:00", "10:00");
        planFragment(activityId, TODAY.plusDays(2), "14:00", "15:00");

        Map<String, Object> activity = findInList((List<?>) getList("/api/activities").body(), activityId);

        assertThat(activity.get("totalFragmentCount")).isEqualTo(3);
        assertThat(activity.get("plannedDayCount")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) activity.get("days");
        assertThat(days).hasSize(2);
        assertThat(days).anySatisfy(d -> {
            assertThat(d.get("day")).isEqualTo(TODAY.toString());
            assertThat(d.get("plannedMinutes")).isEqualTo(60);
        });
        assertThat(days).anySatisfy(d -> {
            assertThat(d.get("day")).isEqualTo(TODAY.plusDays(2).toString());
            assertThat(d.get("plannedMinutes")).isEqualTo(120);
        });
    }

    // --- US4: cascade delete with fragment count ---------------------------

    @Test
    void deletingAnActivityWithFragmentsWithoutConfirmationStatesTheExactCount() {
        String activityId = createActivity("Course a pied", 45);
        planFragment(activityId, TODAY, "07:00", "07:30");
        planFragment(activityId, TODAY.plusDays(1), "07:00", "07:30");

        ApiResponse response = delete("/api/activities/" + activityId);

        assertThat(response.status()).isEqualTo(409);
        Map<String, Object> body = (Map<String, Object>) response.body();
        assertThat(body.get("reason")).isEqualTo("ACTIVITY_HAS_PLANNED_FRAGMENTS");
        assertThat((String) body.get("message")).contains("2").contains("2");
    }

    @Test
    void confirmedDeleteOfAMultiFragmentActivityCascadesToEveryFragment() {
        String activityId = createActivity("Course a pied", 45);
        planFragment(activityId, TODAY, "07:00", "07:30");
        planFragment(activityId, TODAY.plusDays(1), "07:00", "07:30");

        ApiResponse response = delete("/api/activities/" + activityId + "?confirm=true");

        assertThat(response.status()).isEqualTo(204);
        Integer activityCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity", Integer.class);
        assertThat(activityCount).isZero();
        Integer blockCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM time_block", Integer.class);
        assertThat(blockCount).isZero();
    }
}
