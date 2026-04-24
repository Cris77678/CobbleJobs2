package dev.cobblejobs.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import dev.cobblejobs.CobbleJobs;
import lombok.Data;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Carga y guarda la configuración global del mod desde
 * {@code config/cobblejobs/config.json}.
 *
 * <p>Si el archivo no existe, se crean los valores por defecto y se persisten.
 */
public class ConfigManager {

    private static final Path CONFIG_PATH = Paths.get("config/cobblejobs/config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config config;

    public void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(r, Config.class);
                if (config == null) config = new Config();
                CobbleJobs.LOGGER.info("[Config] Configuración cargada desde disco.");
            } catch (IOException e) {
                CobbleJobs.LOGGER.warn("[Config] No se pudo leer config.json; usando defaults.", e);
                config = new Config();
            }
        } else {
            config = new Config();
            save();
            CobbleJobs.LOGGER.info("[Config] Archivo de configuración creado con valores por defecto.");
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(config, w);
            }
        } catch (IOException e) {
            CobbleJobs.LOGGER.error("[Config] No se pudo guardar config.json.", e);
        }
    }

    public Config get() { return config; }

    // ── POJO de configuración ─────────────────────────────────────────────────

    @Data
    public static class Config {

        @SerializedName("auto_save_interval_ticks")
        private int autoSaveIntervalTicks = 6000; // 5 minutos

        @SerializedName("max_job_switch_cooldown_seconds")
        private int maxJobSwitchCooldownSeconds = 300; // 5 minutos

        @SerializedName("fisher_xp_base")
        private double fisherXpBase = 10.0;

        @SerializedName("butcher_xp_base")
        private double butcherXpBase = 15.0;

        @SerializedName("economy_currency_symbol")
        private String economyCurrencySymbol = "$";

        @SerializedName("dynamic_events_enabled")
        private boolean dynamicEventsEnabled = true;

        @SerializedName("anti_afk_enabled")
        private boolean antiAfkEnabled = true;

        @SerializedName("debug_mode")
        private boolean debugMode = false;
    }
}
