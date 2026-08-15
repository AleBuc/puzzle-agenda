/**
 * Repository ports (domain-owned interfaces implemented by infrastructure
 * adapters).
 *
 * <ul>
 *   <li>{@link alebuc.puzzleagenda.domain.port.HorizonStateRepository} and
 *       {@link alebuc.puzzleagenda.domain.port.MaterializedDayRepository} —
 *       defined in the Foundational phase (their entities already existed).</li>
 *   <li>{@link alebuc.puzzleagenda.domain.port.TimeBlockRepository} —
 *       defined in tasks.md T024/US1, alongside the {@code TimeBlock} entity.</li>
 * </ul>
 *
 * <p>{@code ActivityRepository} and {@code RoutineTemplateRepository} are
 * intentionally <strong>not</strong> defined yet: their entities
 * ({@code Activity}, {@code RoutineTemplateEntry}) are introduced in later
 * user-story tasks (tasks.md T040/US2, T060/US4 respectively). Each is
 * added to this package alongside its entity, in the same task, to avoid a
 * forward reference to a type that doesn't exist yet.
 */
package alebuc.puzzleagenda.domain.port;
