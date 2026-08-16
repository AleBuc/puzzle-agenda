package alebuc.puzzleagenda.infrastructure.persistence;

import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.activity.ActivityStatus;
import alebuc.puzzleagenda.domain.activity.Priority;
import alebuc.puzzleagenda.domain.port.ActivityRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC adapter for {@code activity} (V1 migration). {@code status} is never
 * stored — it's computed per row via an {@code EXISTS} subquery against
 * {@code time_block} (data-model.md Activity: derived, not persisted).
 */
@Repository
public class ActivityRepositoryAdapter implements ActivityRepository {

    private static final String SELECT_BASE = """
            SELECT a.id, a.name, a.estimated_duration_minutes, a.priority, a.category,
                   CASE WHEN EXISTS (
                       SELECT 1 FROM time_block tb
                       WHERE tb.activity_id = a.id AND tb.type = 'PLANNED_ACTIVITY'
                   ) THEN 'PLANNED' ELSE 'UNPLANNED' END AS status
            FROM activity a
            """;

    private static final RowMapper<Activity> ROW_MAPPER = ActivityRepositoryAdapter::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public ActivityRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Activity> findById(UUID id) {
        List<Activity> rows = jdbc.query(
                SELECT_BASE + " WHERE a.id = :id", new MapSqlParameterSource("id", id), ROW_MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public List<Activity> findAll() {
        return jdbc.query(SELECT_BASE + " ORDER BY a.name", ROW_MAPPER);
    }

    @Override
    public void save(Activity activity) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", activity.id())
                .addValue("name", activity.name())
                .addValue("estimatedDurationMinutes", activity.estimatedDurationMinutes())
                .addValue("priority", activity.priority().name())
                .addValue("category", activity.category());

        jdbc.update(
                """
                INSERT INTO activity (id, name, estimated_duration_minutes, priority, category)
                VALUES (:id, :name, :estimatedDurationMinutes, :priority, :category)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    estimated_duration_minutes = EXCLUDED.estimated_duration_minutes,
                    priority = EXCLUDED.priority,
                    category = EXCLUDED.category
                """,
                params);
    }

    @Override
    public void deleteById(UUID id) {
        jdbc.update("DELETE FROM activity WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    private static Activity mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Activity.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getInt("estimated_duration_minutes"),
                Priority.valueOf(rs.getString("priority")),
                rs.getString("category"),
                ActivityStatus.valueOf(rs.getString("status")));
    }
}
