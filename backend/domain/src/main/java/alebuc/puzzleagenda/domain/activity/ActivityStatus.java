package alebuc.puzzleagenda.domain.activity;

/**
 * An {@code Activity}'s derived status (spec Key Entities: Activity) — never
 * stored, computed from whether a non-deleted {@code PLANNED_ACTIVITY}
 * {@code TimeBlock} currently references it (data-model.md Activity).
 */
public enum ActivityStatus {
    UNPLANNED,
    PLANNED
}
