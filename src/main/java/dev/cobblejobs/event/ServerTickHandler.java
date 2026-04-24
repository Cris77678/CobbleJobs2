package dev.cobblejobs.event;

import dev.cobblejobs.CobbleJobs;
import dev.cobblejobs.data.PlayerDataBundle;
import dev.cobblejobs.data.PlayerDataManager;
import dev.cobblejobs.job.Job;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Gestiona la lógica de tick del servidor de forma eficiente.
 *
 * <p><b>Optimización clave:</b> itera SOLO los jugadores que tienen un trabajo
 * activo (no en pausa), evitando procesar al 100% de los jugadores en cada tick.
 *
 * <p>Registrado en {@link CobbleJobs} vía {@code ServerTickEvents.END_SERVER_TICK}.
 */
public class ServerTickHandler {

    private final PlayerDataManager dataManager;
    private final DynamicEventManager dynamicEventManager;
    private final AtomicLong tickCount = new AtomicLong(0);

    public ServerTickHandler(PlayerDataManager dataManager,
                              DynamicEventManager dynamicEventManager) {
        this.dataManager = dataManager;
        this.dynamicEventManager = dynamicEventManager;
    }

    public void onServerTick(MinecraftServer server) {
        long tick = tickCount.incrementAndGet();

        // ── Procesar solo jugadores con trabajo activo ────────────────────────
        for (var bundle : dataManager.getAllCached()) {
            if (!bundle.hasActiveJob()) continue;

            String jobId = bundle.getJobId();
            Job job = CobbleJobs.getInstance().getJobRegistry().get(jobId);
            if (job == null) continue;

            var player = server.getPlayerManager().getPlayer(bundle.getUuid());
            if (player == null) continue; // desconectado pero no eviccionado aún

            try {
                job.onServerTick(player, bundle, tick);
            } catch (Exception e) {
                CobbleJobs.LOGGER.error("[TickHandler] Error en tick de {} para {}: {}",
                        jobId, bundle.getUuid(), e.getMessage());
            }
        }

        // ── Tick de eventos dinámicos (cada 20 ticks = 1 segundo) ─────────────
        if (tick % 20 == 0) {
            dynamicEventManager.onSecondTick(server, tick / 20);
        }
    }
}
