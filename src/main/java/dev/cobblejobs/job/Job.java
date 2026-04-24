package dev.cobblejobs.job;

import dev.cobblejobs.data.PlayerDataBundle;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Contrato base que debe implementar cada profesión del mod.
 *
 * <p>El diseño es modular: cada trabajo es un objeto independiente que
 * el {@link JobRegistry} registra. Para añadir una nueva profesión,
 * basta con implementar esta interfaz y registrarla en
 * {@link JobRegistry#registerDefaults()}.
 */
public interface Job {

    /** Identificador único (ej: "fisher", "butcher"). */
    String getId();

    /** Nombre display con colores (ej: "§bPescador"). */
    String getDisplayName();

    /** Descripción corta para el menú de selección. */
    String getDescription();

    /** Nivel máximo de esta profesión. */
    int getMaxLevel();

    /**
     * Lógica por tick del servidor. Se llama desde {@link dev.cobblejobs.event.ServerTickHandler}
     * SOLO si el jugador tiene este trabajo activo y no está en pausa.
     *
     * @param player el jugador
     * @param bundle sus datos en memoria
     * @param tickCount tick absoluto del servidor (útil para timers)
     */
    void onServerTick(ServerPlayerEntity player, PlayerDataBundle bundle, long tickCount);

    /**
     * Llamado cuando el jugador asigna este trabajo.
     * Útil para mostrar tutoriales o preparar estado inicial.
     */
    default void onJobAssigned(ServerPlayerEntity player, PlayerDataBundle bundle) {}

    /**
     * Llamado cuando el jugador abandona este trabajo.
     * Útil para limpiar minijuegos activos, barra de jefe, etc.
     */
    default void onJobRemoved(ServerPlayerEntity player, PlayerDataBundle bundle) {}
}
