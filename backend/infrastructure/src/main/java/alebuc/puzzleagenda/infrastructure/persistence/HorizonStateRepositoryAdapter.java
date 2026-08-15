package alebuc.puzzleagenda.infrastructure.persistence;

import alebuc.puzzleagenda.domain.horizon.HorizonState;
import alebuc.puzzleagenda.domain.port.HorizonStateRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * JDBC adapter for the singleton {@code horizon_state} row (V3 migration).
 *
 * <p>Not itself a tasks.md task: {@code GetHorizon} (T018, Foundational)
 * needs a working {@link HorizonStateRepository} bean to be wireable, so
 * this adapter is provided alongside it. (No later task in tasks.md covers
 * it, unlike {@code MaterializedDayRepositoryAdapter}, which is correctly
 * deferred to T064/US4 since nothing in Foundational or US1 calls it yet.)
 */
@Repository
public class HorizonStateRepositoryAdapter implements HorizonStateRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public HorizonStateRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public HorizonState load() {
        List<LocalDate> rows = jdbc.query(
                "SELECT day1 FROM horizon_state LIMIT 1",
                (rs, rowNum) -> rs.getObject("day1", LocalDate.class));
        if (rows.isEmpty() || rows.get(0) == null) {
            return HorizonState.notYetEstablished();
        }
        return HorizonState.withDay1(rows.get(0));
    }

    @Override
    public void save(HorizonState horizonState) {
        LocalDate day1 = horizonState.day1().orElse(null);
        MapSqlParameterSource params = new MapSqlParameterSource("day1", day1);

        int updated = jdbc.update("UPDATE horizon_state SET day1 = :day1", params);
        if (updated == 0) {
            jdbc.update("INSERT INTO horizon_state (day1) VALUES (:day1)", params);
        }
    }
}
