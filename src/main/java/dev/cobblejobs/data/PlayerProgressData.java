package dev.cobblejobs.data;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Archivo: {@code <UUID>_data.json}
 *
 * Almacena el progreso (XP, nivel, estadísticas acumuladas) del jugador
 * para cada trabajo. Se guarda en un archivo SEPARADO de {@link PlayerJobState}
 * para evitar que una escritura de progreso machaque el estado del trabajo
 * (y viceversa) en condiciones de alta concurrencia.
 *
 * Estructura interna:
 * <pre>
 * {
 *   "job_progress": {
 *     "fisher": { "level": 12, "xp": 3400, ... },
 *     "butcher": { "level": 3, "xp": 500, ... }
 *   }
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
public class PlayerProgressData {

    /**
     * Mapa de jobId → snapshot de progreso.
     * Usa un inner record serializable para mantener todo cohesionado.
     */
    @SerializedName("job_progress")
    private Map<String, JobSnapshot> jobProgress = new HashMap<>();

    // ── API de acceso ─────────────────────────────────────────────────────────

    /** Obtiene (o crea) el snapshot de un trabajo. */
    public JobSnapshot getOrCreate(String jobId) {
        return jobProgress.computeIfAbsent(jobId, id -> new JobSnapshot());
    }

    public boolean hasProgressFor(String jobId) {
        return jobProgress.containsKey(jobId);
    }

    // ── Inner class: snapshot de un trabajo concreto ─────────────────────────

    @Data
    @NoArgsConstructor
    public static class JobSnapshot {

        @SerializedName("level")
        private int level = 1;

        @SerializedName("xp")
        private long xp = 0L;

        /** Acumulado total de ítems procesados (peces pescados, mobs cazados…). */
        @SerializedName("total_processed")
        private long totalProcessed = 0L;

        /** Número de veces que completó el minijuego con éxito. */
        @SerializedName("minigame_wins")
        private long minigameWins = 0L;

        /** Mejor peso (kg * 100 para evitar floats) registrado, por tipo. */
        @SerializedName("best_weight_map")
        private Map<String, Integer> bestWeightMap = new HashMap<>();

        // ── XP helpers ───────────────────────────────────────────────────────

        /**
         * XP requerida para subir del nivel {@code lvl} al {@code lvl+1}.
         * Escala exponencial: base 100, factor 1.15 por nivel.
         */
        public static long xpRequiredForLevel(int lvl) {
            return (long) (100 * Math.pow(1.15, lvl - 1));
        }

        /**
         * Añade XP y sube de nivel si corresponde.
         *
         * @param amount XP a añadir (puede ser multiplicada por eventos dinámicos).
         * @param maxLevel Nivel máximo permitido por el trabajo.
         * @return true si hubo al menos un level-up.
         */
        public boolean addXp(long amount, int maxLevel) {
            if (level >= maxLevel) return false;

            xp += amount;
            boolean leveledUp = false;

            while (level < maxLevel && xp >= xpRequiredForLevel(level)) {
                xp -= xpRequiredForLevel(level);
                level++;
                leveledUp = true;
            }
            return leveledUp;
        }

        public long xpToNextLevel() {
            return xpRequiredForLevel(level) - xp;
        }

        public float progressPercent() {
            long required = xpRequiredForLevel(level);
            return required == 0 ? 100f : (xp * 100f) / required;
        }
    }
}
