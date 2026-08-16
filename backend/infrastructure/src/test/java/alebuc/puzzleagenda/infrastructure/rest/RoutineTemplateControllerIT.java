package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.infrastructure.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(RoutineTemplateControllerIT.MutableClockConfig.class)
class RoutineTemplateControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    /**
     * Overrides {@code UseCaseConfig.clock()} (different bean name, marked
     * {@code @Primary} — same type, no name collision) so
     * {@code aPastDayIsNeverMaterialized} can advance "today" mid-test: Day 1
     * only ever becomes *today at the moment it's established* (never the day
     * targeted), so there is no way to make a genuinely past day reachable
     * without time actually having passed since Day 1 was set.
     */
    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return new MutableDateClock();
        }
    }

    static final class MutableDateClock extends Clock {
        static volatile LocalDate currentDate = TODAY;
        private final ZoneId zone;

        MutableDateClock() {
            this(ZoneId.systemDefault());
        }

        private MutableDateClock(ZoneId zone) {
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableDateClock(zone);
        }

        @Override
        public Instant instant() {
            return currentDate.atStartOfDay(zone).toInstant();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        MutableDateClock.currentDate = TODAY;
        restClient = RestClient.create("http://localhost:" + port);
        jdbcTemplate.update("TRUNCATE TABLE time_block, activity, routine_template_entry, materialized_day, horizon_state");
    }

    private record ApiResponse(int status, Map<String, Object> body) {
    }

    private record ListResponse(int status, List<?> body) {
    }

    private ApiResponse post(String uri, Object body) {
        return restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeMapBody(response)));
    }

    private ApiResponse put(String uri, Object body) {
        return restClient.put().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeMapBody(response)));
    }

    private ApiResponse get(String uri) {
        return restClient.get().uri(uri)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeMapBody(response)));
    }

    private ListResponse getList(String uri) {
        return restClient.get().uri(uri)
                .exchange((request, response) -> new ListResponse(response.getStatusCode().value(), response.bodyTo(List.class)));
    }

    private ApiResponse delete(String uri) {
        return restClient.method(HttpMethod.DELETE).uri(uri)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(), safeMapBody(response)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> safeMapBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws java.io.IOException {
        if (response.getStatusCode().value() == 204) {
            return null;
        }
        return (Map<String, Object>) response.bodyTo(Map.class);
    }

    @Test
    void createsAnEntryAndListsIt() {
        ApiResponse created = post("/api/routine-template/entries",
                Map.of("name", "Sleep", "startTime", "23:00", "endTime", "07:00"));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body()).containsEntry("name", "Sleep").containsEntry("startTime", "23:00");

        ListResponse entries = getList("/api/routine-template/entries");
        assertThat(entries.body()).hasSize(1);
    }

    @Test
    void rejectsAnOverlappingEntryUsingTheTwoDayProjectionRule() {
        post("/api/routine-template/entries", Map.of("name", "Sleep", "startTime", "23:00", "endTime", "07:00"));

        ApiResponse conflicting = post("/api/routine-template/entries",
                Map.of("name", "Early jog", "startTime", "06:30", "endTime", "07:00"));

        assertThat(conflicting.status()).isEqualTo(409);
        assertThat(conflicting.body()).containsEntry("reason", "TEMPLATE_ENTRY_OVERLAP");
    }

    @Test
    void acceptsAnAdjacentEntryRightAfterAMidnightSpanningOne() {
        post("/api/routine-template/entries", Map.of("name", "Sleep", "startTime", "23:00", "endTime", "07:00"));

        ApiResponse adjacent = post("/api/routine-template/entries",
                Map.of("name", "Jog", "startTime", "07:00", "endTime", "07:30"));

        assertThat(adjacent.status()).isEqualTo(201);
    }

    @Test
    void editsAnEntry() {
        ApiResponse created = post("/api/routine-template/entries",
                Map.of("name", "Lunch", "startTime", "12:00", "endTime", "13:00"));
        String id = (String) created.body().get("id");

        ApiResponse edited = put("/api/routine-template/entries/" + id,
                Map.of("name", "Lunch break", "startTime", "12:30", "endTime", "13:15"));

        assertThat(edited.status()).isEqualTo(200);
        assertThat(edited.body()).containsEntry("name", "Lunch break").containsEntry("startTime", "12:30");
    }

    @Test
    void editingANonexistentEntryReturns404() {
        ApiResponse response = put("/api/routine-template/entries/" + UUID.randomUUID(),
                Map.of("name", "X", "startTime", "09:00", "endTime", "10:00"));

        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    void deletesAnEntry() {
        ApiResponse created = post("/api/routine-template/entries",
                Map.of("name", "Lunch", "startTime", "12:00", "endTime", "13:00"));
        String id = (String) created.body().get("id");

        ApiResponse deleted = delete("/api/routine-template/entries/" + id);
        assertThat(deleted.status()).isEqualTo(204);

        ListResponse entries = getList("/api/routine-template/entries");
        assertThat(entries.body()).isEmpty();
    }

    @Test
    void deletingANonexistentEntryReturns404() {
        ApiResponse response = delete("/api/routine-template/entries/" + UUID.randomUUID());

        assertThat(response.status()).isEqualTo(404);
    }

    // --- Materialization on first view (FR-017) -----------------------------

    @Test
    void firstViewOfATodayOrFutureDayMaterializesItFromTheTemplate() {
        post("/api/routine-template/entries", Map.of("name", "Sleep", "startTime", "23:00", "endTime", "07:00"));

        ApiResponse day = get("/api/days/" + TODAY);

        assertThat(day.status()).isEqualTo(200);
        assertThat(day.body()).containsEntry("materialized", true);
        List<?> blocks = (List<?>) day.body().get("blocks");
        assertThat(blocks).hasSize(1);
        Map<String, Object> sleepBlock = (Map<String, Object>) blocks.get(0);
        assertThat(sleepBlock).containsEntry("type", "ROUTINE").containsEntry("name", "Sleep").containsEntry("endsNextDay", true);
    }

    @Test
    void materializationIsIdempotentOnRepeatedViews() {
        post("/api/routine-template/entries", Map.of("name", "Sleep", "startTime", "23:00", "endTime", "07:00"));

        get("/api/days/" + TODAY);
        ApiResponse secondView = get("/api/days/" + TODAY);

        assertThat((List<?>) secondView.body().get("blocks")).hasSize(1);
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM time_block WHERE type = 'ROUTINE'", Integer.class);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void templateEditsAfterMaterializationDoNotAlterTheAlreadyMaterializedDay() {
        post("/api/routine-template/entries", Map.of("name", "Sleep", "startTime", "23:00", "endTime", "07:00"));
        get("/api/days/" + TODAY); // materializes TODAY with just "Sleep"

        post("/api/routine-template/entries", Map.of("name", "Lunch", "startTime", "12:30", "endTime", "13:15"));
        ApiResponse dayAfterTemplateEdit = get("/api/days/" + TODAY);

        List<?> blocks = (List<?>) dayAfterTemplateEdit.body().get("blocks");
        assertThat(blocks).hasSize(1); // still just Sleep — Lunch was added to the template afterward
    }

    @Test
    void materializationClipsAgainstAPreExistingBlockOnTheFollowingDay() {
        LocalDate tomorrow = TODAY.plusDays(1);
        LocalDate dayAfterTomorrow = TODAY.plusDays(2);

        // Pre-existing jog block on dayAfterTomorrow, before tomorrow is materialized.
        post("/api/days/" + dayAfterTomorrow + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "06:00", "endTime", "06:30", "name", "Jog"));
        post("/api/routine-template/entries", Map.of("name", "Sleep", "startTime", "23:00", "endTime", "07:00"));

        ApiResponse day = get("/api/days/" + tomorrow);

        List<?> blocks = (List<?>) day.body().get("blocks");
        assertThat(blocks).hasSize(2); // Sleep clipped to 23:00-06:00, plus a 06:30-07:00 remainder
        assertThat(blocks).allSatisfy(b -> assertThat((Map<String, Object>) b).containsEntry("name", "Sleep"));
    }

    @Test
    void aPastDayIsNeverMaterialized() {
        // Establish Day 1 at TODAY (2026-08-20), then advance the clock so TODAY becomes a past
        // (but still reachable) day, and confirm re-viewing it never triggers materialization.
        post("/api/days/" + TODAY + "/blocks",
                Map.of("type", "CONSTRAINED", "startTime", "09:00", "endTime", "10:00"));
        post("/api/routine-template/entries", Map.of("name", "Sleep", "startTime", "23:00", "endTime", "07:00"));

        MutableDateClock.currentDate = TODAY.plusDays(5);

        ApiResponse day = get("/api/days/" + TODAY);

        assertThat(day.status()).isEqualTo(200);
        assertThat(day.body()).containsEntry("materialized", false);
        List<?> blocks = (List<?>) day.body().get("blocks");
        assertThat(blocks).hasSize(1); // only the manually-created block, no Sleep
    }
}
