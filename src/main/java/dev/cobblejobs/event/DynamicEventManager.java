package dev.cobblejobs.event;

import dev.cobblejobs.CobbleJobs;
import dev.cobblejobs.data.PlayerDataManager;
import dev.cobblejobs.util.MessageUtil;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.MinecraftServer;

import java.util.Random;

/**
 * Gestiona los eventos dinámicos globales del servidor:
 * <ul>
 *   <li>§6Frenesí§r — multiplicador de XP x2 durante 5 minutos</li>
 *   <li>§dBendición§r — multiplicador de rareza x1.5 durante 3 minutos</li>
 *   <li>§bLluvia Legendaria§r — XP x3 + rareza x2 durante 2 minutos</li>
 * </ul>
 *
 * <p>Los eventos ocurren aleatoriamente cada ~15–45 minutos de juego real.
 * Solo aplican a jugadores con trabajo activo.
 */
public class DynamicEventManager {

    private static final Random RNG = new Random();

    // ── Intervalos en segundos (configurables en el futuro) ───────────────────
    private static final int MIN_INTERVAL_SECS = 15 * 60;   // 15 min
    private static final int MAX_INTERVAL_SECS = 45 * 60;   // 45 min

    // ── Estado del evento activo ──────────────────────────────────────────────
    @Getter private DynamicEvent activeEvent = null;
    private long eventEndSecond = 0;
    private long nextEventSecond = 0;

    private MinecraftServer server;

    // ────────────────────────────────────────────────────────────────────────

    public void setServer(MinecraftServer server) {
        this.server = server;
        // Primer evento: entre 10 y 30 minutos después de iniciar
        nextEventSecond = 600 + RNG.nextInt(1200);
    }

    /** Llamado cada segundo de juego real por el {@link ServerTickHandler}. */
    public void onSecondTick(MinecraftServer server, long second) {
        // ── Finalizar evento activo ───────────────────────────────────────────
        if (activeEvent != null && second >= eventEndSecond) {
            MessageUtil.broadcast(server,
                    activeEvent.endColor + "§lEL EVENTO §r" + activeEvent.displayName
                            + activeEvent.endColor + "§l HA TERMINADO.");
            activeEvent = null;
        }

        // ── Iniciar nuevo evento ──────────────────────────────────────────────
        if (activeEvent == null && second >= nextEventSecond) {
            startRandomEvent(server, second);
        }
    }

    private void startRandomEvent(MinecraftServer server, long second) {
        DynamicEvent[] events = DynamicEvent.values();
        activeEvent = events[RNG.nextInt(events.length)];
        eventEndSecond = second + activeEvent.durationSecs;
        nextEventSecond = second
                + MIN_INTERVAL_SECS
                + RNG.nextInt(MAX_INTERVAL_SECS - MIN_INTERVAL_SECS);

        MessageUtil.broadcast(server,
                "§8══════════════════════════════");
        MessageUtil.broadcast(server,
                activeEvent.startColor + "§l⚡ EVENTO: " + activeEvent.displayName + " §l⚡");
        MessageUtil.broadcast(server, activeEvent.description);
        MessageUtil.broadcast(server,
                "§7Duración: §e" + (activeEvent.durationSecs / 60) + " minutos");
        MessageUtil.broadcast(server,
                "§8══════════════════════════════");

        CobbleJobs.LOGGER.info("[DynamicEvents] Evento iniciado: {}", activeEvent.name());
    }

    // ── Getters de multiplicadores ────────────────────────────────────────────

    public float getXpMultiplier() {
        return activeEvent != null ? activeEvent.xpMultiplier : 1f;
    }

    public float getRarityMultiplier() {
        return activeEvent != null ? activeEvent.rarityMultiplier : 1f;
    }

    // ── Enum de eventos ───────────────────────────────────────────────────────

    public enum DynamicEvent {
        FRENZY(
                "§6⚡ Frenesí ⚡",
                "§7¡Todos los pescadores y cazadores ganan §6XP x2§7!",
                "§6", "§6",
                2.0f, 1.0f,
                5 * 60
        ),
        BLESSING(
                "§d✦ Bendición ✦",
                "§7¡La rareza de los encuentros ha aumentado §dx1.5§7!",
                "§d", "§d",
                1.0f, 1.5f,
                3 * 60
        ),
        LEGENDARY_RAIN(
                "§b★ Lluvia Legendaria ★",
                "§7¡§bXP x3 §7y §brarezas x2§7 para todos los trabajadores!",
                "§b", "§b",
                3.0f, 2.0f,
                2 * 60
        );

        public final String displayName;
        public final String description;
        public final String startColor;
        public final String endColor;
        public final float  xpMultiplier;
        public final float  rarityMultiplier;
        public final int    durationSecs;

        DynamicEvent(String displayName, String description,
                     String startColor, String endColor,
                     float xpMultiplier, float rarityMultiplier, int durationSecs) {
            this.displayName = displayName;
            this.description = description;
            this.startColor = startColor;
            this.endColor = endColor;
            this.xpMultiplier = xpMultiplier;
            this.rarityMultiplier = rarityMultiplier;
            this.durationSecs = durationSecs;
        }
    }
}
