package alebuc.puzzleagenda.infrastructure.persistence;

import alebuc.puzzleagenda.domain.port.TimeBlockRepository;
import alebuc.puzzleagenda.domain.timeblock.BlockType;
import alebuc.puzzleagenda.domain.timeblock.TimeBlock;
import alebuc.puzzleagenda.domain.timeblock.TimeRange;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC adapter for {@code time_block} (V1 migration). Maps to/from the
 * plain {@code start_at}/{@code end_at} columns only — the {@code span
 * tsrange} column is {@code GENERATED ALWAYS}, so it is never written, only
 * queried (via {@code &&}) for {@link #findIntersecting}.
 */
@Repository
public class TimeBlockRepositoryAdapter implements TimeBlockRepository {

    private static final RowMapper<TimeBlock> ROW_MAPPER = TimeBlockRepositoryAdapter::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public TimeBlockRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TimeBlock> findById(UUID id) {
        List<TimeBlock> rows = jdbc.query(
                "SELECT id, type, start_at, end_at, name, activity_id FROM time_block WHERE id = :id",
                new MapSqlParameterSource("id", id),
                ROW_MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<TimeBlock> findByActivityId(UUID activityId) {
        List<TimeBlock> rows = jdbc.query(
                "SELECT id, type, start_at, end_at, name, activity_id FROM time_block WHERE activity_id = :activityId",
                new MapSqlParameterSource("activityId", activityId),
                ROW_MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public List<TimeBlock> findByDay(LocalDate day) {
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
        return jdbc.query(
                """
                SELECT id, type, start_at, end_at, name, activity_id FROM time_block
                WHERE start_at >= :dayStart AND start_at < :dayEnd
                ORDER BY start_at
                """,
                new MapSqlParameterSource("dayStart", dayStart).addValue("dayEnd", dayEnd),
                ROW_MAPPER);
    }

    @Override
    public List<TimeBlock> findIntersecting(TimeRange range) {
        return jdbc.query(
                """
                SELECT id, type, start_at, end_at, name, activity_id FROM time_block
                WHERE span && tsrange(:start, :end, '[)')
                """,
                new MapSqlParameterSource("start", range.start()).addValue("end", range.end()),
                ROW_MAPPER);
    }

    @Override
    public void save(TimeBlock block) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", block.id())
                .addValue("type", block.type().name())
                .addValue("startAt", block.range().start())
                .addValue("endAt", block.range().end())
                .addValue("name", block.name())
                .addValue("activityId", block.activityId().orElse(null));

        jdbc.update(
                """
                INSERT INTO time_block (id, type, start_at, end_at, name, activity_id)
                VALUES (:id, :type, :startAt, :endAt, :name, :activityId)
                ON CONFLICT (id) DO UPDATE SET
                    type = EXCLUDED.type,
                    start_at = EXCLUDED.start_at,
                    end_at = EXCLUDED.end_at,
                    name = EXCLUDED.name,
                    activity_id = EXCLUDED.activity_id
                """,
                params);
    }

    @Override
    public void deleteById(UUID id) {
        jdbc.update("DELETE FROM time_block WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    private static TimeBlock mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        BlockType type = BlockType.valueOf(rs.getString("type"));
        LocalDateTime start = rs.getObject("start_at", LocalDateTime.class);
        LocalDateTime end = rs.getObject("end_at", LocalDateTime.class);
        String name = rs.getString("name");
        UUID activityId = rs.getObject("activity_id", UUID.class);
        return TimeBlock.create(id, type, new TimeRange(start, end), name, activityId);
    }
}
