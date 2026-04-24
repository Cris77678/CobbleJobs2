package dev.cobblejobs.job.impl;

import dev.cobblejobs.CobbleJobs;
import dev.cobblejobs.core.FishingZone;
import dev.cobblejobs.data.PlayerDataBundle;
import dev.cobblejobs.job.Job;
import dev.cobblejobs.job.minigame.ResistanceFishingMinigame;
import dev.cobblejobs.job.minigame.ResistanceFishingMinigame.FishRarity;
import dev.cobblejobs.util.FishItemCreator;
import dev.cobblejobs.util.MessageUtil;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profesión: §bPescador (Fisher)
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
    @Override public String getDescription() { return "§7Pesca Cobblemon en zonas mágicas y descubre especies raras."; }
    @Override public int    getMaxLevel()    { return MAX_LEVEL; }

    // ── Ciclo de vida del trabajo ────────────────────────────────────────────

    @Override
    public void onJobAssigned(ServerPlayerEntity player, PlayerDataBundle bundle) {
        registerFabricEvents();

        MessageUtil.sendSuccess(player, "§b¡Bienvenido al trabajo de §lPescador§b!");
        MessageUtil.sendInfo(player,
                "§7Busca las §bzonas de pesca mágicas §7(con partículas) y usa tu caña.");
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

        // 5. Renderizar partículas de las zonas de pesca
        spawnZoneParticles(player, tickCount);

        // 6. Comprobar si el anzuelo está en una zona válida para iniciar la picada
        checkFishingZones(player, tickCount);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers privados
    // ════════════════════════════════════════════════════════════════════════

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

    private void deliverReward(ServerPlayerEntity player, PlayerDataBundle bundle, UUID uuid) {
        FishRarity rarity   = minigame.getPendingRarity(uuid);
        String     species  = minigame.getPendingFishName(uuid); 
        minigame.endSession(uuid, true);
        
        if (rarity == null || species == null) return;

        boolean isShiny = RNG.nextFloat() < 0.015f; 
        double weight = 0.5 + (RNG.nextDouble() * 15.0);
        int length = 10 + RNG.nextInt(120);

        ItemStack reward = FishItemCreator.create(species, weight, length, isShiny, rarity);
        player.getInventory().offerOrDrop(reward);

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

    // ── Zonas de Pesca ────────────────────────────────────────────────────────

    private void spawnZoneParticles(ServerPlayerEntity player, long tickCount) {
        if (tickCount % 10 != 0) return; // Se ejecuta cada medio segundo

        ServerWorld world = (ServerWorld) player.getWorld();
        String dim = world.getRegistryKey().getValue().toString();
        var zones = CobbleJobs.getInstance().getConfigManager().get().getFishingZones();

        for (FishingZone zone : zones) {
            if (!zone.getDimension().equals(dim)) continue;
            
            // Renderizar partículas solo si el jugador está a menos de 64 bloques
            if (player.squaredDistanceTo(zone.getCenterX(), zone.getCenterY(), zone.getCenterZ()) > 4096) continue;

            int area = (zone.getMaxX() - zone.getMinX()) * (zone.getMaxZ() - zone.getMinZ());
            int particlesToSpawn = Math.min(40, Math.max(5, area / 2));

            for (int i = 0; i < particlesToSpawn; i++) {
                double x = zone.getMinX() + RNG.nextDouble() * (zone.getMaxX() - zone.getMinX() + 1);
                double y = zone.getMaxY() + 0.2; // Flotando sobre el agua
                double z = zone.getMinZ() + RNG.nextDouble() * (zone.getMaxZ() - zone.getMinZ() + 1);

                // Estrellas mágicas
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 1, 0, 0, 0, 0.05);
                
                // Conchas marinas raras
                if (RNG.nextFloat() < 0.2f) {
                    world.spawnParticles(ParticleTypes.NAUTILUS, x, y, z, 1, 0, 0, 0, 0.02);
                }
            }
        }
    }

    private void checkFishingZones(ServerPlayerEntity player, long tickCount) {
        if (minigame.hasActiveSession(player.getUuid())) return;

        FishingBobberEntity hook = player.fishHook;
        if (hook == null || !hook.isTouchingWater()) return;

        String dim = player.getWorld().getRegistryKey().getValue().toString();
        boolean inZone = CobbleJobs.getInstance().getConfigManager().get().getFishingZones()
                .stream().anyMatch(z -> z.contains(hook.getX(), hook.getY(), hook.getZ(), dim));

        if (!inZone) return;

        // Probabilidad de picada si está en la zona (15% cada segundo)
        if (tickCount % 20 != 0) return;
        if (RNG.nextFloat() > 0.15f) return; 

        float rarityMult = CobbleJobs.getInstance().getDynamicEventManager().getRarityMultiplier();
        FishRarity rarity = rollRarity(rarityMult);
        String species = pickSpecies(rarity);

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
