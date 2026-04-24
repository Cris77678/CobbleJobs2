package dev.cobblejobs.util;

import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Utilidad centralizada para todos los mensajes del mod.
 *
 * <p>Principios:
 * <ul>
 *   <li>Prefijo de mod uniforme en todos los mensajes de chat.</li>
 *   <li>Colores semánticos: INFO=gris, SUCCESS=verde, WARN=amarillo, ERROR=rojo.</li>
 *   <li>Soporte para títulos en pantalla y ActionBar.</li>
 *   <li>Difusión a todos los jugadores en línea.</li>
 * </ul>
 */
public final class MessageUtil {

    // ── Prefijo visual del mod ────────────────────────────────────────────────
    private static final String PREFIX_RAW = "§8[§6CobbleJobs§8] §r";

    private MessageUtil() {}

    // ── Mensajes de chat ──────────────────────────────────────────────────────

    public static void sendInfo(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(PREFIX_RAW + "§7" + message), false);
    }

    public static void sendSuccess(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(PREFIX_RAW + "§a" + message), false);
    }

    public static void sendWarning(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(PREFIX_RAW + "§e⚠ " + message), false);
    }

    public static void sendError(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(PREFIX_RAW + "§c✖ " + message), false);
    }

    /** Mensaje especial de level-up con formato llamativo. */
    public static void sendLevelUp(ServerPlayerEntity player, String jobName, int newLevel) {
        player.sendMessage(Text.literal(
                PREFIX_RAW + "§6✦ §e¡Subiste al nivel §6" + newLevel + "§e en §b" + jobName + "§e! §6✦"), false);
        sendTitle(player, "§6✦ ¡NIVEL " + newLevel + "! ✦", "§e" + jobName, 10, 40, 10);
    }

    // ── ActionBar (texto sobre la barra de vida) ──────────────────────────────

    public static void sendActionBar(ServerPlayerEntity player, String message) {
        player.networkHandler.sendPacket(
                new OverlayMessageS2CPacket(Text.literal(message)));
    }

    // ── Títulos en pantalla ───────────────────────────────────────────────────

    /**
     * Envía un título + subtítulo al jugador.
     *
     * @param fadeIn  ticks de entrada
     * @param stay    ticks de permanencia
     * @param fadeOut ticks de salida
     */
    public static void sendTitle(ServerPlayerEntity player,
                                  String title, String subtitle,
                                  int fadeIn, int stay, int fadeOut) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title)));
        if (subtitle != null && !subtitle.isEmpty()) {
            player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                            Text.literal(subtitle)));
        }
    }

    // ── Difusión al servidor ──────────────────────────────────────────────────

    /** Envía un mensaje a todos los jugadores conectados. */
    public static void broadcast(MinecraftServer server, String message) {
        server.getPlayerManager().getPlayerList()
                .forEach(p -> p.sendMessage(Text.literal(PREFIX_RAW + message), false));
    }

    /** Envía un mensaje sólo a jugadores con el trabajo {@code jobId} activo. */
    public static void broadcastToJob(MinecraftServer server,
                                       dev.cobblejobs.data.PlayerDataManager dataManager,
                                       String jobId, String message) {
        server.getPlayerManager().getPlayerList().forEach(p -> {
            var bundle = dataManager.getCached(p.getUuid());
            if (bundle != null && jobId.equals(bundle.getJobId())) {
                p.sendMessage(Text.literal(PREFIX_RAW + message), false);
            }
        });
    }

    // ── Componentes de texto enriquecidos ─────────────────────────────────────

    public static MutableText colorize(String text, Formatting formatting) {
        return Text.literal(text).formatted(formatting);
    }

    /** Construye una barra de progreso visual con caracteres Unicode. */
    public static String progressBar(float percent, int totalChars) {
        int filled = (int) (percent / 100f * totalChars);
        return "§a" + "█".repeat(filled)
                + "§8" + "░".repeat(totalChars - filled)
                + " §e" + String.format("%.1f%%", percent);
    }
}
