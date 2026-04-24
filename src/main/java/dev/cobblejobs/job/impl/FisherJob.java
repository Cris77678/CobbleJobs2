package dev.cobblejobs.job.impl;

import dev.cobblejobs.CobbleJobs;
import dev.cobblejobs.data.PlayerDataBundle;
import dev.cobblejobs.job.Job;
import dev.cobblejobs.job.minigame.ResistanceFishingMinigame;
import dev.cobblejobs.job.minigame.ResistanceFishingMinigame.FishRarity;
import dev.cobblejobs.util.FishItemCreator;
import dev.cobblejobs.util.MessageUtil;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profesión: §bPescador (Fisher)
 *
 * <h2>Mecánicas implementadas</h2>
 * <ol>
 * <li>Anti-AFK por posición XZ.</li>
 * <li>Minijuego de resistencia {@link ResistanceFishingMinigame}:
 * Fase A reacción + Fase B tensión con ciclos por rareza.</li>
 * <li>Clic izquierdo capturado vía {@code AttackBlockCallback} de Fabric API.</li>
 * <li>Sneak detectado via {@code player.isSneaking()} en cada tick.</li>
 * <li>Entrega de ítem y XP al completar todos los ciclos.</li>
 * <li>Limpieza de BossBar y recursos al perder el trabajo o desconectarse.</li>
 * </ol>
 */
public class FisherJob implements Job {

    public static final String ID        = "fisher";
    public static final int    MAX_LEVEL = 50;

    // ── XP por rareza ─────────────────────────────────────────────────────────
    private static final int XP_COMMON    = 10;
    private static final int XP_RARE      = 25;
    private static final int XP_EPIC      = 60;
    private static final int XP_LEGENDARY = 150;

    // ── Probabilidades de rareza base ─────────────────────────────────────────
    private static final float PROB_LEGENDARY = 2f;
    private static final float PROB_EPIC      = 8f;
    private static final float PROB_RARE      = 25f;

    private static final Random RNG = new Random();

    // ── Anti-AFK ──────────────────────────────────────────────────────────────
    private final Map<UUID, double[]> lastPositions = new ConcurrentHashMap<>();

    // ── Minijuego (singleton) ─────────────────────────────────────────────────
    private final ResistanceFishingMinigame minigame = ResistanceFishingMinigame.getInstance();

    // ── Flag para registrar eventos Fabric una sola vez ───────────────────────
    private volatile boolean eventsRegistered = false;

    // ════════════════════════════════════════════════════════════════════════
    @Override public String getId()          { return ID; }
    @Override public String getDisplayName() { return "§bPescador"; }
    @Override public String getDescription() { return "§7Pesca Cobblemon y descubre especies raras."; }
    @Override public int    getMaxLevel()    { return MAX_LEVEL; }

    // ── Ciclo de vida del trabajo ────────────────────────────────────────────

    @Override
    public void onJobAssigned(ServerPlayerEntity player, PlayerDataBundle bundle) {
        registerFabricEvents();

        MessageUtil.sendSuccess(player, "§b¡Bienvenido al trabajo de §lPescador§b!");
        MessageUtil.sendInfo(player,
                "§7Usa tu §ecaña de pescar §7y reacciona cuando aparezca §e§l¡TIRA!");
        lastPositions.put(player.getUuid(),
                new double[]{player.getX(), player.getY(), player.getZ()});
    }

    @Override
    public void onJobRemoved(ServerPlayerEntity player, PlayerDataBundle bundle) {
        UUID uuid = player.getUuid();
        minigame.cancelSession(player);
        minigame.endSession(uuid, false);
        lastPositions.remove(uuid);
        CobbleJobs.LOGGER.debug("[Fisher] Sesión y recursos limpiados para {}", uuid);
    }

    // ── Tick principal ───────────────────────────────────────────────────────

    @Override
    public void onServerTick(ServerPlayerEntity player, PlayerDataBundle bundle, long tickCount) {
        UUID uuid = player.getUuid();

        // 1. Propagar estado de Sneak al minijuego cada tick
        minigame.onSneakChange(player, player.isSneaking());

        // 2. Tick del minijuego
        minigame.onTick(player, tickCount);

        // 3. Entregar recompensa si la sesión terminó con victoria
        if (minigame.isPendingReward(uuid)) {
            deliverReward(player, bundle, uuid);
        }

        // 4. Cada segundo: anti-AFK + ActionBar de nivel (solo fuera del minijuego)
        if (tickCount % 20 == 0) {
            checkAntiAfk(player);
            if (!minigame.hasActiveSession(uuid)) {
                showLevelBar(player, bundle);
            }
        }

        // 5. Demo de picada simulada (reemplazar por evento real de Cobblemon)
        simulateFishBiteDemo(player, tickCount);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers privados
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Registra el listener de Clic Izquierdo de Fabric (idempotente).
     * AttackBlockCallback se dispara cuando el jugador hace clic izquierdo,
     * incluyendo ataques en el aire en la mayoría de versiones recientes.
     */
    private synchronized void registerFabricEvents() {
        if (eventsRegistered) return;
        eventsRegistered = true;

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!minigame.hasActiveSession(sp.getUuid())) return ActionResult.PASS;

            long tick = world.getServer() != null ? world.getServer().getTicks() : 0L;
            boolean consumed = minigame.onLeftClick(sp, tick);
            return consumed ? ActionResult.SUCCESS : ActionResult.PASS;
        });

        CobbleJobs.LOGGER.info("[Fisher] AttackBlockCallback registrado para detección de clic.");
    }

    /** Entrega el ítem y XP cuando el minijuego reporta victoria. */
    private void deliverReward(ServerPlayerEntity player, PlayerDataBundle bundle, UUID uuid) {
        FishRarity rarity   = minigame.getPendingRarity(uuid);
        String     species  = minigame.getPendingFishName(uuid); // Ahora recibe IDs como "cobblemon:magikarp"
        minigame.endSession(uuid, true);
        
        if (rarity == null || species == null) return;

        // --- Generación de estadísticas aleatorias ---
        boolean isShiny = RNG.nextFloat() < 0.015f; 
        double weight = 0.5 + (RNG.nextDouble() * 15.0);
        int length = 10 + RNG.nextInt(120);

        // --- Creación del ítem real usando el FishItemCreator ---
        ItemStack reward = FishItemCreator.create(species, weight, length, isShiny, rarity);
        player.getInventory().offerOrDrop(reward);

        // --- Lógica de XP ---
        int xp = switch (rarity) {
            case COMMON    -> XP_COMMON;
            case RARE      -> XP_RARE;
            case EPIC      -> XP_EPIC;
            case LEGENDARY -> XP_LEGENDARY;
        };

        float xpMult = CobbleJobs.getInstance().getDynamicEventManager().getXpMultiplier();
        int finalXp  = Math.round(xp * xpMult);

        var snapshot = bundle.getProgress(ID);
        boolean leveledUp = snapshot.addXp((long) finalXp, getMaxLevel());
        if (leveledUp) {
            MessageUtil.sendLevelUp(player, getDisplayName(), snapshot.getLevel());
        }

        MessageUtil.sendInfo(player,
                "§7+ §e" + finalXp + " XP §7de pescador"
                        + (xpMult > 1f ? " §6(x" + String.format("%.1f", xpMult) + " evento)" : ""));

        CobbleJobs.LOGGER.info("[Fisher] Recompensa entregada: {} | {} {} | {} XP",
                uuid, rarity, species, finalXp);
    }

    /**
     * Demo de picada: cada ~10 s, 20 % de chance si el jugador lleva caña y no
     * tiene sesión activa. Reemplazar por el hook real de Cobblemon en producción.
     */
    private void simulateFishBiteDemo(ServerPlayerEntity player, long tickCount) {
        if (tickCount % 200 != 0) return;
        if (minigame.hasActiveSession(player.getUuid())) return;
        if (player.getMainHandStack().getItem() != Items.FISHING_ROD) return;
        if (RNG.nextFloat() > 0.20f) return;

        float rarityMult = CobbleJobs.getInstance().getDynamicEventManager().getRarityMultiplier();
        FishRarity rarity = rollRarity(rarityMult);
        String species    = pickSpecies(rarity); // Cambiado a pickSpecies para obtener IDs reales

        minigame.startSession(player, rarity, species, tickCount);
    }

    private FishRarity rollRarity(float mult) {
        float roll = RNG.nextFloat() * 100f;
        float leg  = PROB_LEGENDARY * mult;
        float epic = PROB_EPIC      * mult;
        float rare = PROB_RARE      * mult;

        if (roll < leg)                    return FishRarity.LEGENDARY;
        if (roll < leg + epic)             return FishRarity.EPIC;
        if (roll < leg + epic + rare)      return FishRarity.RARE;
        return FishRarity.COMMON;
    }

    /**
     * Devuelve el ID de la especie de Cobblemon para el minijuego.
     */
    private String pickSpecies(FishRarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> "cobblemon:kyogre";
            case EPIC      -> "cobblemon:gyarados";
            case RARE      -> "cobblemon:goldeen";
            case COMMON    -> "cobblemon:magikarp";
        };
    }

    // ── Anti-AFK ──────────────────────────────────────────────────────────────

    private void checkAntiAfk(ServerPlayerEntity player) {
        UUID uuid    = player.getUuid();
        double[] cur = {player.getX(), player.getY(), player.getZ()};
        double[] last = lastPositions.getOrDefault(uuid, cur);
        double dist   = Math.hypot(cur[0] - last[0], cur[2] - last[2]);

        if (dist < 0.1 && minigame.hasActiveSession(uuid)) {
            CobbleJobs.LOGGER.trace("[Fisher] Anti-AFK: {} quieto durante minijuego.", uuid);
        }
        lastPositions.put(uuid, cur);
    }

    // ── ActionBar de nivel ────────────────────────────────────────────────────

    private void showLevelBar(ServerPlayerEntity player, PlayerDataBundle bundle) {
        var snap = bundle.getProgress(ID);
        String bar = MessageUtil.progressBar(snap.progressPercent(), 20);
        MessageUtil.sendActionBar(player,
                "§bPescador §8| §7Nv §e" + snap.getLevel()
                        + " §8| " + bar
                        + " §8| §7" + snap.xpToNextLevel() + " XP para subir");
    }
}
