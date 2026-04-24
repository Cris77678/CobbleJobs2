package dev.cobblejobs.job.minigame;

import dev.cobblejobs.CobbleJobs;
import dev.cobblejobs.util.MessageUtil;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minijuego de pesca por resistencia para CobbleJobs — Pescador.
 *
 * <h2>Flujo de sesión</h2>
 * <pre>
 *  startSession()
 *      └─► PHASE_A_REACTION  (jugador debe hacer clic izquierdo en ≤ reactionWindow ticks)
 *              └─ onLeftClick()
 *                      └─► PHASE_B_TENSION  (mantener tensión verde durante holdTicks)
 *                              └─ onSneakChange() / tick()
 *                                      └─ si completa ─► siguiente ciclo o victoria
 *                                      └─ si falla    ─► failSession()
 * </pre>
 *
 * <h2>Rarezas y ciclos requeridos</h2>
 * <ul>
 *   <li>COMMON    → 1 ciclo</li>
 *   <li>RARE      → 2 ciclos</li>
 *   <li>EPIC      → 3 ciclos</li>
 *   <li>LEGENDARY → 5 ciclos</li>
 * </ul>
 *
 * <h2>Fase A — Reacción (The Hook)</h2>
 * Aparece un título "§e§l¡TIRA!" durante {@value #REACTION_WINDOW_TICKS} ticks (~1 s).
 * El jugador debe hacer clic izquierdo antes de que expire.
 *
 * <h2>Fase B — Tensión (The Struggle)</h2>
 * Una BossBar muestra la tensión del sedal (0–100 %).
 * Zona segura: [SAFE_MIN, SAFE_MAX].
 * – Sin Shift: la tensión cae {@value #TENSION_DECAY} / tick.
 * – Con Shift: la tensión sube {@value #TENSION_RISE} / tick.
 * El jugador debe mantener la barra en verde durante {@value #HOLD_TICKS} ticks (~4 s).
 * Si la tensión toca 0 % o 100 %, el sedal se rompe.
 */
public class ResistanceFishingMinigame {

    // ── Constantes de dificultad ──────────────────────────────────────────────
    /** Ticks para reaccionar en Fase A (~1 segundo). */
    public static final int REACTION_WINDOW_TICKS = 20;

    /** Ticks en zona verde necesarios para completar Fase B (~4 segundos). */
    public static final int HOLD_TICKS = 80;

    /** Límite inferior de la zona segura (verde). */
    public static final float SAFE_MIN = 35f;

    /** Límite superior de la zona segura (verde). */
    public static final float SAFE_MAX = 65f;

    /** Tensión inicial al comenzar Fase B (centro). */
    public static final float TENSION_START = 50f;

    /** Caída de tensión por tick cuando el jugador NO presiona Shift. */
    public static final float TENSION_DECAY = 0.6f;

    /** Subida de tensión por tick cuando el jugador presiona Shift. */
    public static final float TENSION_RISE  = 1.0f;

    /** Delay en ticks entre ciclos (~0.5 segundos). */
    public static final int INTER_CYCLE_DELAY_TICKS = 10;

    // ── Ciclos por rareza ─────────────────────────────────────────────────────
    public enum FishRarity {
        COMMON(1,    "§7Común"),
        RARE(2,      "§9Raro"),
        EPIC(3,      "§5Épico"),
        LEGENDARY(5, "§6Legendario");

        public final int requiredSuccesses;
        public final String displayName;

        FishRarity(int requiredSuccesses, String displayName) {
            this.requiredSuccesses = requiredSuccesses;
            this.displayName = displayName;
        }
    }

    // ── Fases internas ────────────────────────────────────────────────────────
    public enum Phase {
        /** Minijuego inactivo. */
        IDLE,
        /** Esperando clic izquierdo del jugador. */
        PHASE_A_REACTION,
        /** El jugador lucha contra la tensión con Shift. */
        PHASE_B_TENSION,
        /** Breve pausa entre ciclos antes de la próxima Fase A. */
        INTER_CYCLE_PAUSE,
        /** Sesión terminada (éxito o fallo). */
        FINISHED
    }

    // ── Estado de sesión por jugador ──────────────────────────────────────────
    private static class Session {
        final FishRarity rarity;
        final String     fishName;         // nombre del Cobblemon / ítem a entregar
        Phase   phase            = Phase.IDLE;
        int     currentCycle     = 1;      // ciclo en curso (1-based)
        long    phaseStartTick   = 0;      // tick global en que comenzó la fase actual
        float   tension          = TENSION_START;
        int     greenTicksHeld   = 0;      // ticks acumulados en zona verde
        boolean sneaking         = false;

        /** BossBar de tensión (Phase B). Null fuera de Phase B. */
        ServerBossBar bossBar = null;

        Session(FishRarity rarity, String fishName) {
            this.rarity   = rarity;
            this.fishName = fishName;
        }
    }

    // ── Instancia única por mod ───────────────────────────────────────────────
    private static final ResistanceFishingMinigame INSTANCE = new ResistanceFishingMinigame();
    public static ResistanceFishingMinigame getInstance() { return INSTANCE; }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private ResistanceFishingMinigame() {}

    // ═════════════════════════════════════════════════════════════════════════
    // API pública
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Inicia una nueva sesión de pesca para el jugador.
     * Llama a esto cuando el anzuelo tiene un pez enganchado.
     *
     * @param player   jugador que pesca
     * @param rarity   rareza del pez detectado
     * @param fishName nombre del Cobblemon / ítem para mostrar en mensajes
     * @param tick     tick actual del servidor
     */
    public void startSession(ServerPlayerEntity player, FishRarity rarity, String fishName, long tick) {
        UUID uuid = player.getUuid();

        // Cancelar sesión previa si existiera
        endSession(uuid, false);

        Session session = new Session(rarity, fishName);
        sessions.put(uuid, session);

        CobbleJobs.LOGGER.debug("[Fishing] Sesión iniciada para {} | {} | {} ciclos",
                uuid, fishName, rarity.requiredSuccesses);

        beginPhaseA(player, session, tick);
    }

    /**
     * Debe llamarse desde el handler de {@code PlayerInteractEvent} / paquete de ataque
     * cuando el jugador hace clic izquierdo mientras tiene una sesión activa.
     *
     * @return {@code true} si el clic fue consumido por el minijuego
     */
    public boolean onLeftClick(ServerPlayerEntity player, long tick) {
        Session session = sessions.get(player.getUuid());
        if (session == null || session.phase != Phase.PHASE_A_REACTION) return false;

        long elapsed = tick - session.phaseStartTick;
        if (elapsed <= REACTION_WINDOW_TICKS) {
            // ¡Reacción exitosa!
            beginPhaseB(player, session, tick);
        } else {
            // El tiempo ya expiró; el fallo lo gestiona onTick()
        }
        return true;
    }

    /**
     * Debe llamarse cuando el estado de Sneak del jugador cambia.
     * Usa {@code ServerPlayerEntity#isSneaking()} o el paquete correspondiente.
     */
    public void onSneakChange(ServerPlayerEntity player, boolean sneaking) {
        Session session = sessions.get(player.getUuid());
        if (session == null) return;
        session.sneaking = sneaking;
    }

    /**
     * Debe invocarse en cada tick del servidor para los jugadores con sesión activa.
     * Delégalo desde {@code FisherJob#onServerTick}.
     *
     * @param player jugador
     * @param tick   tick global del servidor
     */
    public void onTick(ServerPlayerEntity player, long tick) {
        Session session = sessions.get(player.getUuid());
        if (session == null || session.phase == Phase.IDLE || session.phase == Phase.FINISHED) return;

        switch (session.phase) {
            case PHASE_A_REACTION -> tickPhaseA(player, session, tick);
            case PHASE_B_TENSION  -> tickPhaseB(player, session, tick);
            case INTER_CYCLE_PAUSE -> tickInterCycle(player, session, tick);
            default -> { /* no-op */ }
        }
    }

    /**
     * Limpia la sesión del jugador sin dar premio.
     * Llama esto en {@code onJobRemoved} y en el evento de desconexión.
     */
    public void cancelSession(ServerPlayerEntity player) {
        endSession(player.getUuid(), false);
    }

    /** Devuelve {@code true} si el jugador tiene una sesión activa. */
    public boolean hasActiveSession(UUID uuid) {
        Session s = sessions.get(uuid);
        return s != null && s.phase != Phase.IDLE && s.phase != Phase.FINISHED;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Lógica interna de fases
    // ═════════════════════════════════════════════════════════════════════════

    // ── Fase A: Reacción ──────────────────────────────────────────────────────

    private void beginPhaseA(ServerPlayerEntity player, Session session, long tick) {
        session.phase          = Phase.PHASE_A_REACTION;
        session.phaseStartTick = tick;

        // Sonido de lanzamiento al iniciar cada ciclo
        player.getWorld().playSound(null,
                player.getBlockPos(),
                SoundEvents.ENTITY_FISHING_BOBBER_THROW,
                SoundCategory.PLAYERS,
                1.0f, 1.0f + (float)(Math.random() * 0.2 - 0.1));

        // Título "§e§l¡TIRA!"
        MessageUtil.sendTitle(player,
                "§e§l¡TIRA!",
                "§7Ciclo §e" + session.currentCycle + "§7/§e" + session.rarity.requiredSuccesses,
                2, REACTION_WINDOW_TICKS - 4, 2);
    }

    private void tickPhaseA(ServerPlayerEntity player, Session session, long tick) {
        long elapsed = tick - session.phaseStartTick;
        if (elapsed > REACTION_WINDOW_TICKS) {
            // Tiempo agotado en Fase A → fallo
            failSession(player, session, "§c¡Demasiado lento! El pez escapó.");
        }
    }

    // ── Fase B: Tensión ───────────────────────────────────────────────────────

    private void beginPhaseB(ServerPlayerEntity player, Session session, long tick) {
        session.phase          = Phase.PHASE_B_TENSION;
        session.phaseStartTick = tick;
        session.tension        = TENSION_START;
        session.greenTicksHeld = 0;

        // Crear BossBar
        String barTitle = session.rarity.displayName + " §r§7— " + session.fishName;
        session.bossBar = new ServerBossBar(
                Text.literal(barTitle),
                BossBar.Color.GREEN,
                BossBar.Style.NOTCHED_10
        );
        session.bossBar.setPercent(session.tension / 100f);
        session.bossBar.addPlayer(player);
    }

    private void tickPhaseB(ServerPlayerEntity player, Session session, long tick) {
        // ── Actualizar tensión ────────────────────────────────────────────────
        if (session.sneaking) {
            session.tension = Math.min(100f, session.tension + TENSION_RISE);
        } else {
            session.tension = Math.max(0f, session.tension - TENSION_DECAY);
        }

        // ── Comprobar límites (sedal roto) ────────────────────────────────────
        if (session.tension <= 0f || session.tension >= 100f) {
            String reason = session.tension <= 0f
                    ? "§c¡El sedal se aflojó! El pez escapó."
                    : "§c§l¡SNAP! §cEl sedal se rompió.";
            failSession(player, session, reason);
            return;
        }

        // ── Actualizar BossBar color + progreso ───────────────────────────────
        boolean inSafeZone = session.tension >= SAFE_MIN && session.tension <= SAFE_MAX;
        if (session.bossBar != null) {
            session.bossBar.setColor(inSafeZone ? BossBar.Color.GREEN : BossBar.Color.RED);
            session.bossBar.setPercent(session.tension / 100f);
        }

        // ── Contar ticks en zona verde ────────────────────────────────────────
        if (inSafeZone) {
            session.greenTicksHeld++;
        } else {
            // Penalización suave: restablece el contador si sale de la zona
            session.greenTicksHeld = Math.max(0, session.greenTicksHeld - 2);
        }

        // ── Partículas alrededor del jugador ──────────────────────────────────
        if (tick % 4 == 0) {
            spawnFishingParticles(player);
        }

        // ── ActionBar con progreso de tensión ─────────────────────────────────
        if (tick % 2 == 0) {
            updateTensionActionBar(player, session);
        }

        // ── Comprobar si completó el tiempo requerido ─────────────────────────
        if (session.greenTicksHeld >= HOLD_TICKS) {
            completeCycle(player, session, tick);
        }
    }

    // ── Pausa inter-ciclo ─────────────────────────────────────────────────────

    private void completeCycle(ServerPlayerEntity player, Session session, long tick) {
        removeBossBar(session);

        if (session.currentCycle >= session.rarity.requiredSuccesses) {
            // ¡Todos los ciclos completados → victoria!
            winSession(player, session);
        } else {
            // Aún quedan ciclos
            session.currentCycle++;
            session.phase          = Phase.INTER_CYCLE_PAUSE;
            session.phaseStartTick = tick;

            MessageUtil.sendActionBar(player,
                    "§a§l✔ §r§7¡Sigue luchando! §e(Ciclo "
                            + session.currentCycle + "/" + session.rarity.requiredSuccesses + ")");
        }
    }

    private void tickInterCycle(ServerPlayerEntity player, Session session, long tick) {
        if (tick - session.phaseStartTick >= INTER_CYCLE_DELAY_TICKS) {
            beginPhaseA(player, session, tick);
        }
    }

    // ── Victoria / Derrota ────────────────────────────────────────────────────

    private void winSession(ServerPlayerEntity player, Session session) {
        session.phase = Phase.FINISHED;
        removeBossBar(session);

        // Sonido de victoria solo en el win final
        player.getWorld().playSound(null,
                player.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS,
                1.0f, 1.2f);

        // Título de victoria
        MessageUtil.sendTitle(player,
                "§a§l¡CAPTURADO!",
                session.rarity.displayName + " §r§7" + session.fishName,
                5, 50, 10);

        MessageUtil.sendSuccess(player,
                "§a✔ ¡Capturaste un §r" + session.rarity.displayName
                        + " §a" + session.fishName + "§a!");

        CobbleJobs.LOGGER.debug("[Fishing] Victoria para {} | {}", player.getUuid(), session.fishName);

        // Notificar al FisherJob para que entregue el ítem
        // (el job escucha hasActiveSession() == false y phase == FINISHED con win)
        sessions.put(player.getUuid(), session); // conservar state=FINISHED para que FisherJob lo lea
    }

    private void failSession(ServerPlayerEntity player, Session session, String reason) {
        session.phase = Phase.FINISHED;
        removeBossBar(session);

        player.getWorld().playSound(null,
                player.getBlockPos(),
                SoundEvents.ENTITY_FISHING_BOBBER_SPLASH,
                SoundCategory.PLAYERS,
                0.8f, 0.7f);

        MessageUtil.sendTitle(player,
                "§c§l✖ ¡ESCAPÓ!",
                "§7" + session.fishName + " se fue...",
                3, 30, 8);

        MessageUtil.sendError(player, reason);

        CobbleJobs.LOGGER.debug("[Fishing] Fallo para {} | {}", player.getUuid(), session.fishName);

        sessions.remove(player.getUuid());
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Limpia la sesión. Si {@code giveReward} es false, solo limpia recursos.
     * FisherJob debe llamar a esto después de entregar el ítem cuando es victoria.
     */
    public void endSession(UUID uuid, boolean consumed) {
        Session session = sessions.remove(uuid);
        if (session != null) {
            if (session.bossBar != null) {
                session.bossBar.clearPlayers();
                session.bossBar = null;
            }
        }
    }

    /**
     * Devuelve {@code true} si la sesión terminó con victoria y el ítem aún
     * no fue entregado. FisherJob debe consultar esto en cada tick.
     */
    public boolean isPendingReward(UUID uuid) {
        Session s = sessions.get(uuid);
        return s != null && s.phase == Phase.FINISHED;
    }

    /**
     * Devuelve la rareza del pez pendiente de recompensa.
     * Retorna {@code null} si no hay sesión finalizada.
     */
    public FishRarity getPendingRarity(UUID uuid) {
        Session s = sessions.get(uuid);
        return (s != null && s.phase == Phase.FINISHED) ? s.rarity : null;
    }

    /**
     * Devuelve el nombre del pez pendiente de recompensa.
     */
    public String getPendingFishName(UUID uuid) {
        Session s = sessions.get(uuid);
        return (s != null && s.phase == Phase.FINISHED) ? s.fishName : null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers visuales
    // ═════════════════════════════════════════════════════════════════════════

    private void removeBossBar(Session session) {
        if (session.bossBar != null) {
            session.bossBar.clearPlayers();
            session.bossBar = null;
        }
    }

    /**
     * Genera partículas de burbujas y salpicaduras alrededor del jugador
     * para simular la tensión del sedal.
     */
    private void spawnFishingParticles(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) return;

        double x = player.getX() + (Math.random() * 1.6 - 0.8);
        double y = player.getY() + 0.1;
        double z = player.getZ() + (Math.random() * 1.6 - 0.8);

        // Burbujas
        serverWorld.spawnParticles(
                ParticleTypes.BUBBLE,
                x, y, z,
                3,               // count
                0.1, 0.05, 0.1,  // offset
                0.05             // speed
        );

        // Salpicadura
        if (System.currentTimeMillis() % 600 < 50) {
            serverWorld.spawnParticles(
                    ParticleTypes.SPLASH,
                    x, y + 0.05, z,
                    2, 0.15, 0.0, 0.15, 0.05
            );
        }
    }

    /**
     * Actualiza la ActionBar con la barra de tensión visual y el progreso del ciclo.
     */
    private void updateTensionActionBar(ServerPlayerEntity player, Session session) {
        boolean inZone = session.tension >= SAFE_MIN && session.tension <= SAFE_MAX;

        // Barra de tensión (20 caracteres)
        int totalChars  = 20;
        int safeStart   = (int)(SAFE_MIN / 100f * totalChars);
        int safeEnd     = (int)(SAFE_MAX / 100f * totalChars);
        int markerPos   = (int)(session.tension / 100f * totalChars);
        markerPos = Math.min(totalChars - 1, Math.max(0, markerPos));

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < totalChars; i++) {
            if (i == markerPos) {
                bar.append(inZone ? "§a§l◆" : "§c§l◆");
            } else if (i >= safeStart && i <= safeEnd) {
                bar.append("§2▓");
            } else {
                bar.append("§8░");
            }
        }

        int progressPct = (int)((float)session.greenTicksHeld / HOLD_TICKS * 100f);
        String progressColor = inZone ? "§a" : "§7";

        MessageUtil.sendActionBar(player,
                "§7[" + bar + "§7] "
                        + progressColor + progressPct + "% "
                        + "§8| §7Ciclo §e" + session.currentCycle
                        + "§7/§e" + session.rarity.requiredSuccesses);
    }
}
