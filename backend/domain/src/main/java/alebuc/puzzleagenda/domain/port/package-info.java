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
 *   <li>{@link alebuc.puzzleagenda.domain.port.ActivityRepository} —
 *       defined in tasks.md T040/US2, alongside the {@code Activity} entity.</li>
 * </ul>
 *
 * <p>{@code RoutineTemplateRepository} is intentionally <strong>not</strong>
 * defined yet: its entity ({@code RoutineTemplateEntry}) is introduced in
 * tasks.md T060/US4, where the port is added alongside it, to avoid a
 * forward reference to a type that doesn't exist yet.
 */
package alebuc.puzzleagenda.domain.port;
