/**
 * Repository ports (domain-owned interfaces implemented by infrastructure
 * adapters), each defined alongside the entity it persists, in the
 * user-story task that first needed it:
 *
 * <ul>
 *   <li>{@link alebuc.puzzleagenda.domain.port.HorizonStateRepository} and
 *       {@link alebuc.puzzleagenda.domain.port.MaterializedDayRepository} —
 *       Foundational phase.</li>
 *   <li>{@link alebuc.puzzleagenda.domain.port.TimeBlockRepository} —
 *       tasks.md T024/US1.</li>
 *   <li>{@link alebuc.puzzleagenda.domain.port.ActivityRepository} —
 *       tasks.md T040/US2.</li>
 *   <li>{@link alebuc.puzzleagenda.domain.port.RoutineTemplateRepository} —
 *       tasks.md T060/US4.</li>
 * </ul>
 *
 * <p>All five ports (matching all five entities in data-model.md) now
 * exist; this deferral note no longer applies to any pending task.
 */
package alebuc.puzzleagenda.domain.port;
