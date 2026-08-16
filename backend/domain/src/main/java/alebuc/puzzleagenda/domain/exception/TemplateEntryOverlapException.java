package alebuc.puzzleagenda.domain.exception;

/**
 * Thrown when a new/edited routine template entry overlaps an existing
 * entry, using the two-day projection rule (FR-016). Maps to 409 Conflict /
 * {@code TEMPLATE_ENTRY_OVERLAP} at the API boundary (contracts/api.md
 * Error Conventions) — distinct from {@link TimeBlockOverlapException}
 * since template entries aren't time blocks.
 */
public class TemplateEntryOverlapException extends RuntimeException {

    public TemplateEntryOverlapException(String message) {
        super(message);
    }
}
