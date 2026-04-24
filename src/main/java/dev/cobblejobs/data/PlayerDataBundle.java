package dev.cobblejobs.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Bundle en memoria que agrupa {@link PlayerJobState} y {@link PlayerProgressData}
 * para un jugador. Este objeto vive en el caché del {@link PlayerDataManager};
 * NUNCA se serializa directamente (cada campo se guarda en su propio archivo).
 *
 * Ciclo de vida:
 *  1. Creado por {@link PlayerDataManager#loadPlayerAsync(UUID)}.
 *  2. Modificado por comandos, eventos y mecánicas de trabajo.
 *  3. Guardado por {@link PlayerDataManager#savePlayerAsync(UUID)}.
 *  4. Eviccionado del caché al desconectarse el jugador.
 */
@Getter
@RequiredArgsConstructor
public class PlayerDataBundle {

    private final UUID uuid;

    /** Estado del trabajo activo → {@code <UUID>_job.json} */
    private final PlayerJobState jobState;

    /** Progreso / nivel por trabajo → {@code <UUID>_data.json} */
    private final PlayerProgressData progressData;

    // ── Delegates convenientes ────────────────────────────────────────────────

    public boolean hasActiveJob()       { return jobState.hasActiveJob(); }
    public String  getJobId()           { return jobState.getJobId(); }

    public PlayerProgressData.JobSnapshot getProgress(String jobId) {
        return progressData.getOrCreate(jobId);
    }

    /** Añade XP al trabajo activo actual (si existe). Retorna true si hubo level-up. */
    public boolean addXpToCurrentJob(long amount, int maxLevel) {
        if (!hasActiveJob()) return false;
        return progressData.getOrCreate(getJobId()).addXp(amount, maxLevel);
    }
}
