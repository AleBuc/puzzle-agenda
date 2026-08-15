package alebuc.puzzleagenda.infrastructure.persistence;

import alebuc.puzzleagenda.domain.port.MaterializedDayRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JDBC adapter for {@code materialized_day} (V3 migration). Deferred from
 * the Foundational phase (T014) to here (T064/US4) since nothing called it
 * until {@code ViewDay} started running real materialization (T063).
 */
@Repository
public class MaterializedDayRepositoryAdapter implements MaterializedDayRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MaterializedDayRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isMaterialized(LocalDate day) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM materialized_day WHERE day = :day",
                new MapSqlParameterSource("day", day),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void markMaterialized(LocalDate day, LocalDateTime materializedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("day", day)
                .addValue("materializedAt", materializedAt);
        jdbc.update(
                "INSERT INTO materialized_day (day, materialized_at) VALUES (:day, :materializedAt) ON CONFLICT (day) DO NOTHING",
                params);
    }
}
