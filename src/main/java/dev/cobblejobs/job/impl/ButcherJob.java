package dev.cobblejobs.job.impl;

import dev.cobblejobs.data.PlayerDataBundle;
import dev.cobblejobs.job.Job;
import dev.cobblejobs.util.MessageUtil;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Profesión: §cCazador (Butcher)
 *
 * <p>Mecánicas planeadas:
 * <ol>
 *   <li>Prey Spawner: genera Cobblemon marcados como "presa" en zonas</li>
 *   <li>Entidades con más vida, etiqueta visual [RARE/ELITE] y NBT {@code cd_nocatch}</li>
 *   <li>Recompensas al morir basadas en rareza + nivel del jugador</li>
 *   <li>Loot tables configurables por tier de presa</li>
 * </ol>
 */
public class ButcherJob implements Job {

    public static final String ID        = "butcher";
    public static final int    MAX_LEVEL = 50;

    @Override public String getId()          { return ID; }
    @Override public String getDisplayName() { return "§cCazador"; }
    @Override public String getDescription() { return "§7Caza criaturas Cobblemon de élite para obtener recompensas."; }
    @Override public int    getMaxLevel()    { return MAX_LEVEL; }

    @Override
    public void onServerTick(ServerPlayerEntity player, PlayerDataBundle bundle, long tickCount) {
        if (tickCount % 20 != 0) return;

        var snapshot = bundle.getProgress(ID);
        String bar = MessageUtil.progressBar(snapshot.progressPercent(), 20);
        MessageUtil.sendActionBar(player,
                "§cCazador §8| §7Nv §e" + snapshot.getLevel()
                        + " §8| " + bar
                        + " §8| §7" + snapshot.xpToNextLevel() + " XP para subir");
    }

    @Override
    public void onJobAssigned(ServerPlayerEntity player, PlayerDataBundle bundle) {
        MessageUtil.sendSuccess(player, "§c¡Bienvenido al trabajo de Cazador!");
        MessageUtil.sendInfo(player, "§7Localiza zonas de caza y elimina las presas marcadas.");
    }
}
