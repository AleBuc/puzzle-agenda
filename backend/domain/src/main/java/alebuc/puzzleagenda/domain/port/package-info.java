/**
 * Repository ports (domain-owned interfaces implemented by infrastructure
 * adapters). {@link alebuc.puzzleagenda.domain.port.HorizonStateRepository} and
 * {@link alebuc.puzzleagenda.domain.port.MaterializedDayRepository} are defined
 * here in the Foundational phase because their entities
 * ({@code HorizonState}, {@code MaterializedDay}) already exist.
 *
 * <p>{@code ActivityRepository}, {@code TimeBlockRepository}, and
 * {@code RoutineTemplateRepository} are intentionally <strong>not</strong>
 * defined yet: their entities ({@code Activity}, {@code TimeBlock},
 * {@code RoutineTemplateEntry}) are introduced in later user-story tasks
 * (tasks.md T040/US2, T024/US1, T060/US4 respectively). Each of those ports
 * is added to this package alongside its entity, in the same task, to avoid
 * a forward reference to a type that doesn't exist yet.
 */
package alebuc.puzzleagenda.domain.port;
