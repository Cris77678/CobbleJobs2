package dev.cobblejobs.data;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Archivo: {@code <UUID>_job.json}
 *
 * Almacena el estado del trabajo activo del jugador.
 * Se guarda en un archivo SEPARADO de {@link PlayerProgressData}
 * para evitar colisiones de escritura cuando ambos datos se modifican
 * en momentos distintos (ej: cambio de trabajo vs ganancia de XP).
 */
@Data
@NoArgsConstructor
public class PlayerJobState {

    /** ID del trabajo activo. Null = sin trabajo. */
    @SerializedName("job_id")
    private String jobId = null;

    /** Timestamp (epoch ms) en que se asignó el trabajo. */
    @SerializedName("job_since")
    private long jobSince = 0L;

    /** Si el jugador tiene el trabajo en "pausa" (desactivado temporalmente). */
    @SerializedName("paused")
    private boolean paused = false;

    /** UUID del jugador (redundante pero útil para diagnósticos). */
    @SerializedName("player_uuid")
    private String playerUuid = "";

    // ── Helpers ──────────────────────────────────────────────────────────────

    public boolean hasActiveJob() {
        return jobId != null && !jobId.isBlank() && !paused;
    }

    public void assignJob(String newJobId) {
        this.jobId = newJobId;
        this.jobSince = System.currentTimeMillis();
        this.paused = false;
    }

    public void clearJob() {
        this.jobId = null;
        this.jobSince = 0L;
        this.paused = false;
    }
}
