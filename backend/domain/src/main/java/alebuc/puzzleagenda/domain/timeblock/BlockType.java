package alebuc.puzzleagenda.domain.timeblock;

/**
 * A {@link TimeBlock}'s kind (spec Key Entities: Time Block). Immutable once
 * set on a block (data-model.md TimeBlock).
 */
public enum BlockType {
    ROUTINE,
    CONSTRAINED,
    PLANNED_ACTIVITY
}
