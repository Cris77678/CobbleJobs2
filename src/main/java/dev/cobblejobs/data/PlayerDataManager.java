package dev.cobblejobs.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.cobblejobs.CobbleJobs;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gestiona el ciclo completo de datos de los jugadores:
 * <ul>
 *   <li>Caché en memoria ({@link ConcurrentHashMap})</li>
 *   <li>Carga asíncrona desde disco al conectar</li>
 *   <li>Guardado asíncrono con escritura atómica (temp → rename)</li>
 *   <li>Flush síncrono al cerrar el servidor</li>
 * </ul>
 *
 * <b>Separación de archivos:</b>
 * <pre>
 *   config/cobblejobs/players/<UUID>_job.json   → PlayerJobState
 *   config/cobblejobs/players/<UUID>_data.json  → PlayerProgressData
 * </pre>
 */
public class PlayerDataManager {

    // ── Constantes ────────────────────────────────────────────────────────────
    private static final String DIR_NAME    = "config/cobblejobs/players";
    private static final String SUFFIX_JOB  = "_job.json";
    private static final String SUFFIX_DATA = "_data.json";

    // ── GSON (pretty-print para legibilidad humana) ───────────────────────────
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    // ── Thread pool dedicado al IO ────────────────────────────────────────────
    private static final int IO_THREADS = 2;
    private final ExecutorService ioExecutor;

    // ── Caché en memoria ──────────────────────────────────────────────────────
    private final Map<UUID, PlayerDataBundle> cache = new ConcurrentHashMap<>();

    // ── Referencia al servidor (necesaria para obtener el game dir) ───────────
    private MinecraftServer server;
    private Path playersDir;

    // ────────────────────────────────────────────────────────────────────────
    public PlayerDataManager() {
        AtomicInteger counter = new AtomicInteger(0);
        this.ioExecutor = Executors.newFixedThreadPool(IO_THREADS, r -> {
            Thread t = new Thread(r, "CobbleJobs-IO-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Llamado cuando el servidor ya está disponible. */
    public void setServer(@NotNull MinecraftServer server) {
        this.server = server;
        this.playersDir = server.getRunDirectory().resolve(DIR_NAME);
        try {
            Files.createDirectories(playersDir);
        } catch (IOException e) {
            CobbleJobs.LOGGER.error("[DataManager] No se pudo crear el directorio de datos.", e);
        }
    }

    // ── Carga ─────────────────────────────────────────────────────────────────

    /**
     * Carga los datos de un jugador de forma asíncrona.
     * Si ya están en caché, devuelve inmediatamente.
     *
     * @return CompletableFuture con el bundle listo para usar.
     */
    public CompletableFuture<PlayerDataBundle> loadPlayerAsync(@NotNull UUID uuid) {
        if (cache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(cache.get(uuid));
        }
        return CompletableFuture.supplyAsync(() -> loadFromDisk(uuid), ioExecutor)
                .thenApply(bundle -> {
                    cache.put(uuid, bundle);
                    CobbleJobs.LOGGER.debug("[DataManager] Datos cargados para {}", uuid);
                    return bundle;
                })
                .exceptionally(ex -> {
                    CobbleJobs.LOGGER.error("[DataManager] Error cargando datos de {}", uuid, ex);
                    PlayerDataBundle empty = createEmpty(uuid);
                    cache.put(uuid, empty);
                    return empty;
                });
    }

    /** Retorna el bundle si está en caché, o null. No hace IO. */
    @Nullable
    public PlayerDataBundle getCached(@NotNull UUID uuid) {
        return cache.get(uuid);
    }

    /** Retorna el bundle en caché o carga síncronamente (emergencia). */
    @NotNull
    public PlayerDataBundle getOrLoadSync(@NotNull UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromDisk);
    }

    // ── Guardado ──────────────────────────────────────────────────────────────

    /**
     * Guarda los datos de un jugador de forma asíncrona usando escritura atómica:
     * escribe en un archivo temporal y luego hace rename para evitar corrupción.
     */
    public CompletableFuture<Void> savePlayerAsync(@NotNull UUID uuid) {
        PlayerDataBundle bundle = cache.get(uuid);
        if (bundle == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Captura snapshot en el hilo principal antes de pasarlo al executor
        String jobJson      = GSON.toJson(bundle.getJobState());
        String progressJson = GSON.toJson(bundle.getProgressData());

        return CompletableFuture.runAsync(() -> {
            writeSafe(pathFor(uuid, SUFFIX_JOB),  jobJson);
            writeSafe(pathFor(uuid, SUFFIX_DATA), progressJson);
        }, ioExecutor).exceptionally(ex -> {
            CobbleJobs.LOGGER.error("[DataManager] Error guardando datos de {}", uuid, ex);
            return null;
        });
    }

    /**
     * Guarda TODOS los jugadores en caché de forma asíncrona y espera a que
     * terminen todos. Usado en {@code SERVER_STOPPING}.
     */
    public CompletableFuture<Void> saveAllSync() {
        CompletableFuture<?>[] futures = cache.keySet().stream()
                .map(this::savePlayerAsync)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /** Elimina al jugador del caché (tras guardar). */
    public void evictFromCache(@NotNull UUID uuid) {
        cache.remove(uuid);
    }

    /** Acceso de solo lectura a todos los bundles en caché. */
    public Collection<PlayerDataBundle> getAllCached() {
        return cache.values();
    }

    // ── IO interno ────────────────────────────────────────────────────────────

    @NotNull
    private PlayerDataBundle loadFromDisk(@NotNull UUID uuid) {
        PlayerJobState     jobState     = readOrDefault(pathFor(uuid, SUFFIX_JOB),  PlayerJobState.class);
        PlayerProgressData progressData = readOrDefault(pathFor(uuid, SUFFIX_DATA), PlayerProgressData.class);

        // Rellena el UUID en el estado (por si el archivo no lo tenía)
        jobState.setPlayerUuid(uuid.toString());
        return new PlayerDataBundle(uuid, jobState, progressData);
    }

    @NotNull
    private <T> T readOrDefault(@NotNull Path path, @NotNull Class<T> clazz) {
        if (!Files.exists(path)) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("No se pudo crear instancia de " + clazz.getSimpleName(), e);
            }
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            T result = GSON.fromJson(reader, clazz);
            return result != null ? result : clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            CobbleJobs.LOGGER.warn("[DataManager] Archivo corrupto {}, usando defaults. Error: {}", path, e.getMessage());
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Escritura atómica: escribe en {@code .tmp} y luego hace ATOMIC_MOVE.
     * Garantiza que nunca se lean archivos a medio escribir.
     */
    private void writeSafe(@NotNull Path target, @NotNull String json) {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            CobbleJobs.LOGGER.error("[DataManager] Fallo escritura atómica en {}: {}", target, e.getMessage());
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    @NotNull
    private Path pathFor(@NotNull UUID uuid, @NotNull String suffix) {
        return playersDir.resolve(uuid + suffix);
    }

    @NotNull
    private PlayerDataBundle createEmpty(@NotNull UUID uuid) {
        PlayerJobState state = new PlayerJobState();
        state.setPlayerUuid(uuid.toString());
        return new PlayerDataBundle(uuid, state, new PlayerProgressData());
    }
}
