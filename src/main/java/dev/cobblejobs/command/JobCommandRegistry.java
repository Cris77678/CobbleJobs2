package dev.cobblejobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.cobblejobs.CobbleJobs;
import dev.cobblejobs.data.PlayerDataBundle;
import dev.cobblejobs.data.PlayerDataManager;
import dev.cobblejobs.job.Job;
import dev.cobblejobs.job.JobRegistry;
import dev.cobblejobs.util.MessageUtil;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Registra todos los subcomandos de {@code /job}:
 *
 * <pre>
 *   /job list           — muestra trabajos disponibles
 *   /job join <id>      — asigna un trabajo al jugador
 *   /job leave          — abandona el trabajo actual
 *   /job info           — muestra nivel, XP y progreso
 *   /job sell           — abre el menú de venta (stub)
 * </pre>
 */
public class JobCommandRegistry {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                 PlayerDataManager dataManager,
                                 JobRegistry jobRegistry) {

        dispatcher.register(
            CommandManager.literal("job")

                // /job list
                .then(CommandManager.literal("list")
                    .executes(ctx -> cmdList(ctx.getSource(), jobRegistry)))

                // /job join <id>
                .then(CommandManager.literal("join")
                    .then(CommandManager.argument("job_id", StringArgumentType.word())
                        .executes(ctx -> cmdJoin(ctx.getSource(), dataManager, jobRegistry,
                                StringArgumentType.getString(ctx, "job_id")))))

                // /job leave
                .then(CommandManager.literal("leave")
                    .executes(ctx -> cmdLeave(ctx.getSource(), dataManager, jobRegistry)))

                // /job info
                .then(CommandManager.literal("info")
                    .executes(ctx -> cmdInfo(ctx.getSource(), dataManager, jobRegistry)))

                // /job sell (stub → futuro GUI)
                .then(CommandManager.literal("sell")
                    .executes(ctx -> cmdSell(ctx.getSource())))

                // /job — sin subcomando muestra ayuda
                .executes(ctx -> cmdHelp(ctx.getSource()))
        );

        CobbleJobs.LOGGER.info("[Commands] Comandos /job registrados.");
    }

    // ── Implementaciones ─────────────────────────────────────────────────────

    private static int cmdHelp(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        MessageUtil.sendInfo(player, "§eComandos disponibles:");
        MessageUtil.sendInfo(player, "§7/job list §8— §fVer trabajos");
        MessageUtil.sendInfo(player, "§7/job join <id> §8— §fUnirte a un trabajo");
        MessageUtil.sendInfo(player, "§7/job leave §8— §fAbandonar trabajo");
        MessageUtil.sendInfo(player, "§7/job info §8— §fVer tu progreso");
        MessageUtil.sendInfo(player, "§7/job sell §8— §fVender tu inventario");
        return 1;
    }

    private static int cmdList(ServerCommandSource source, JobRegistry registry) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        MessageUtil.sendInfo(player, "§e§lTrabajos disponibles:");
        for (Job job : registry.getAll()) {
            player.sendMessage(net.minecraft.text.Text.literal(
                    "  §8▶ §f" + job.getDisplayName()
                    + " §8(§7/job join " + job.getId() + "§8) §8— "
                    + job.getDescription()), false);
        }
        return 1;
    }

    private static int cmdJoin(ServerCommandSource source, PlayerDataManager dm,
                                JobRegistry registry, String jobId) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;

        Job job = registry.get(jobId);
        if (job == null) {
            MessageUtil.sendError(player, "Trabajo '§e" + jobId + "§c' no encontrado. Usa §f/job list§c.");
            return 0;
        }

        PlayerDataBundle bundle = dm.getOrLoadSync(player.getUuid());

        if (jobId.equals(bundle.getJobId())) {
            MessageUtil.sendWarning(player, "¡Ya tienes el trabajo §e" + job.getDisplayName() + "§e!");
            return 0;
        }

        // Quitar trabajo anterior
        if (bundle.hasActiveJob()) {
            Job old = registry.get(bundle.getJobId());
            if (old != null) old.onJobRemoved(player, bundle);
        }

        bundle.getJobState().assignJob(jobId);
        job.onJobAssigned(player, bundle);

        // Guardado asíncrono
        dm.savePlayerAsync(player.getUuid());
        return 1;
    }

    private static int cmdLeave(ServerCommandSource source, PlayerDataManager dm,
                                 JobRegistry registry) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;

        PlayerDataBundle bundle = dm.getOrLoadSync(player.getUuid());
        if (!bundle.hasActiveJob()) {
            MessageUtil.sendWarning(player, "No tienes ningún trabajo activo.");
            return 0;
        }

        Job job = registry.get(bundle.getJobId());
        if (job != null) job.onJobRemoved(player, bundle);

        bundle.getJobState().clearJob();
        MessageUtil.sendInfo(player, "§7Has abandonado tu trabajo.");
        dm.savePlayerAsync(player.getUuid());
        return 1;
    }

    private static int cmdInfo(ServerCommandSource source, PlayerDataManager dm,
                                JobRegistry registry) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;

        PlayerDataBundle bundle = dm.getOrLoadSync(player.getUuid());
        if (!bundle.hasActiveJob()) {
            MessageUtil.sendWarning(player, "No tienes ningún trabajo activo. Usa §f/job list§e.");
            return 0;
        }

        Job job = registry.get(bundle.getJobId());
        var snap = bundle.getProgress(bundle.getJobId());
        String jobName = job != null ? job.getDisplayName() : bundle.getJobId();

        MessageUtil.sendInfo(player, "§e§l── Tu Progreso ──");
        MessageUtil.sendInfo(player, "§7Trabajo: " + jobName);
        MessageUtil.sendInfo(player, "§7Nivel: §e" + snap.getLevel() + " §8/ §e"
                + (job != null ? job.getMaxLevel() : "?"));
        MessageUtil.sendInfo(player, "§7XP: §e" + snap.getXp()
                + " §8/ §e" + snap.xpToNextLevel() + " §7para subir");
        MessageUtil.sendInfo(player, "§7Progreso: " + MessageUtil.progressBar(snap.progressPercent(), 20));
        MessageUtil.sendInfo(player, "§7Total procesado: §e" + snap.getTotalProcessed());
        return 1;
    }

    private static int cmdSell(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        // TODO: abrir GUI de venta (SellMenuGui)
        MessageUtil.sendWarning(player, "§7El menú de venta estará disponible próximamente.");
        return 1;
    }
}
