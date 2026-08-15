package alebuc.puzzleagenda.domain.port;

import alebuc.puzzleagenda.domain.horizon.HorizonState;

/** Port for the singleton {@link HorizonState} row (data-model.md HorizonState). */
public interface HorizonStateRepository {

    /** Returns the current state, or {@link HorizonState#notYetEstablished()} if no row exists yet. */
    HorizonState load();

    /** Persists the given state (upsert of the single row). */
    void save(HorizonState horizonState);
}
