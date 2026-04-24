package dev.cobblejobs.job;

import dev.cobblejobs.CobbleJobs;
import dev.cobblejobs.job.impl.ButcherJob;
import dev.cobblejobs.job.impl.FisherJob;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro central de todas las profesiones disponibles.
 *
 * <p>Patrón: {@code LinkedHashMap} ordenado para que los menús siempre
 * muestren los trabajos en el orden en que fueron registrados.
 *
 * <p>Para añadir una nueva profesión desde otro mod (addon):
 * <pre>{@code
 *   CobbleJobs.getInstance().getJobRegistry().register(new MyCustomJob());
 * }</pre>
 */
public class JobRegistry {

    private final Map<String, Job> jobs = new LinkedHashMap<>();

    /** Registra los trabajos incluidos en el mod base. */
    public void registerDefaults() {
        register(new FisherJob());
        register(new ButcherJob());
        CobbleJobs.LOGGER.info("[JobRegistry] {} trabajos registrados: {}",
                jobs.size(), jobs.keySet());
    }

    public void register(@NotNull Job job) {
        if (jobs.containsKey(job.getId())) {
            CobbleJobs.LOGGER.warn("[JobRegistry] Trabajo '{}' ya registrado; se sobreescribirá.", job.getId());
        }
        jobs.put(job.getId(), job);
    }

    @Nullable
    public Job get(@NotNull String id) {
        return jobs.get(id.toLowerCase());
    }

    public boolean exists(@NotNull String id) {
        return jobs.containsKey(id.toLowerCase());
    }

    public Collection<Job> getAll() {
        return Collections.unmodifiableCollection(jobs.values());
    }
}
