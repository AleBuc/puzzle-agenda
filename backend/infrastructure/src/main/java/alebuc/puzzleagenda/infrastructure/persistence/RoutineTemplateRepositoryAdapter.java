package alebuc.puzzleagenda.infrastructure.persistence;

import alebuc.puzzleagenda.domain.port.RoutineTemplateRepository;
import alebuc.puzzleagenda.domain.routine.RoutineTemplateEntry;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@code routine_template_entry} (V2 migration). */
@Repository
public class RoutineTemplateRepositoryAdapter implements RoutineTemplateRepository {

    private static final RowMapper<RoutineTemplateEntry> ROW_MAPPER = RoutineTemplateRepositoryAdapter::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public RoutineTemplateRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RoutineTemplateEntry> findById(UUID id) {
        List<RoutineTemplateEntry> rows = jdbc.query(
                "SELECT id, name, start_time, end_time FROM routine_template_entry WHERE id = :id",
                new MapSqlParameterSource("id", id),
                ROW_MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public List<RoutineTemplateEntry> findAll() {
        return jdbc.query("SELECT id, name, start_time, end_time FROM routine_template_entry ORDER BY start_time", ROW_MAPPER);
    }

    @Override
    public void save(RoutineTemplateEntry entry) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entry.id())
                .addValue("name", entry.name())
                .addValue("startTime", entry.startTime())
                .addValue("endTime", entry.endTime());

        jdbc.update(
                """
                INSERT INTO routine_template_entry (id, name, start_time, end_time)
                VALUES (:id, :name, :startTime, :endTime)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    start_time = EXCLUDED.start_time,
                    end_time = EXCLUDED.end_time
                """,
                params);
    }

    @Override
    public void deleteById(UUID id) {
        jdbc.update("DELETE FROM routine_template_entry WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    private static RoutineTemplateEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        return RoutineTemplateEntry.create(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class));
    }
}
