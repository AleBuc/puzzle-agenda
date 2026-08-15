package alebuc.puzzleagenda.domain.port;

import alebuc.puzzleagenda.domain.activity.Activity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for {@link Activity} persistence (data-model.md Activity). Defined
 * here, alongside the entity, per the deferral documented in
 * {@code package-info.java} (tasks.md T040/US2).
 */
public interface ActivityRepository {

    Optional<Activity> findById(UUID id);

    /** All activities, each with its derived {@code status} already populated, ordered by name. */
    List<Activity> findAll();

    /** Insert-or-update by id. */
    void save(Activity activity);

    void deleteById(UUID id);
}
