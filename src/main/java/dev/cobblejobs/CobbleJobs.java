package dev.cobblejobs;

import dev.cobblejobs.command.JobCommandRegistry;
import dev.cobblejobs.core.ConfigManager;
import dev.cobblejobs.data.PlayerDataManager;
import dev.cobblejobs.event.DynamicEventManager;
import dev.cobblejobs.event.ServerTickHandler;
import dev.cobblejobs.job.JobRegistry;
import dev.cobblejobs.util.MessageUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada principal de CobbleJobs.
 * Gestiona el ciclo de vida del mod y coordina todos los subsistemas.
 */
public class CobbleJobs implements ModInitializer {

    public static final String MOD_ID = "cobblejobs";
    public static final String MOD_NAME = "CobbleJobs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ── Instancia singleton ──────────────────────────────────────────────────
    private static CobbleJobs instance;

    // ── Subsistemas principales ──────────────────────────────────────────────
    private PlayerDataManager playerDataManager;
    private ConfigManager configManager;
    private JobRegistry jobRegistry;
    private DynamicEventManager dynamicEventManager;
    private ServerTickHandler tickHandler;

    // ────────────────────────────────────────────────────────────────────────
    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("[{}] Iniciando mod v{}", MOD_NAME, "1.0.0");

        // 1. Configuración global
        configManager = new ConfigManager();
        configManager.load();

        // 2. Sistema de datos (carga lazy por jugador)
        playerDataManager = new PlayerDataManager();

        // 3. Registro de trabajos disponibles
        jobRegistry = new JobRegistry();
        jobRegistry.registerDefaults();

        // 4. Eventos dinámicos del servidor
        dynamicEventManager = new DynamicEventManager();

        // 5. Handler de ticks eficiente
        tickHandler = new ServerTickHandler(playerDataManager, dynamicEventManager);

        // ── Registro de eventos Fabric ────────────────────────────────────
        registerLifecycleEvents();
        registerPlayerEvents();
        registerTickEvents();
        registerCommands();

        LOGGER.info("[{}] Mod inicializado correctamente.", MOD_NAME);
    }

    // ── Ciclo de vida del servidor ───────────────────────────────────────────
    private void registerLifecycleEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            playerDataManager.setServer(server);
            dynamicEventManager.setServer(server);
            LOGGER.info("[{}] Servidor iniciado. Sistema de datos listo.", MOD_NAME);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[{}] Servidor deteniéndose. Guardando todos los datos...", MOD_NAME);
            playerDataManager.saveAllSync()   // flush síncrono al cerrar
                    .thenRun(() -> LOGGER.info("[{}] Todos los datos guardados.", MOD_NAME));
        });
    }

    // ── Conexión / desconexión de jugadores ─────────────────────────────────
    private void registerPlayerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            // Carga asíncrona al conectar; no bloquea el hilo principal
            playerDataManager.loadPlayerAsync(player.getUuid())
                    .thenAccept(data -> {
                        MessageUtil.sendInfo(player,
                                "§aBienvenido de vuelta a §eCobbleJobs§a, §6" + player.getName().getString() + "§a!");
                        if (data.hasActiveJob()) {
                            MessageUtil.sendInfo(player,
                                    "§7Trabajo activo: §e" + data.getJobId());
                        }
                    });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var uuid = handler.getPlayer().getUuid();
            // Guardado asíncrono al desconectar
            playerDataManager.savePlayerAsync(uuid)
                    .thenRun(() -> playerDataManager.evictFromCache(uuid));
        });
    }

    // ── Ticks del servidor ───────────────────────────────────────────────────
    private void registerTickEvents() {
        // Solo procesa jugadores con trabajo activo
        ServerTickEvents.END_SERVER_TICK.register(tickHandler::onServerTick);
    }

    // ── Comandos ─────────────────────────────────────────────────────────────
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                JobCommandRegistry.register(dispatcher, playerDataManager, jobRegistry));
    }

    // ── Getters estáticos ────────────────────────────────────────────────────
    public static CobbleJobs getInstance()          { return instance; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public ConfigManager getConfigManager()         { return configManager; }
    public JobRegistry getJobRegistry()             { return jobRegistry; }
    public DynamicEventManager getDynamicEventManager() { return dynamicEventManager; }
}
