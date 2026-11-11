package uy.ccisj.api.postulante;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uy.ccisj.api.storage.S3StorageService;

@Component
public class CvFileCleanup {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvFileCleanup.class);
    private final JdbcTemplate jdbc;
    private final S3StorageService storage;

    public CvFileCleanup(JdbcTemplate jdbc, S3StorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void deletePendingFiles() {
        var keys = jdbc.queryForList("""
                SELECT p.ruta FROM cv_archivos_pendientes_borrado p
                WHERE NOT EXISTS (SELECT 1 FROM cvs WHERE ruta_archivo_cv = p.ruta)
                ORDER BY p.creado_at LIMIT 100
                """, String.class);
        for (String key : keys) {
            try {
                storage.deleteObject(key);
                jdbc.update("DELETE FROM cv_archivos_pendientes_borrado WHERE ruta = ?", key);
            } catch (RuntimeException exception) {
                LOGGER.error("No se pudo borrar un archivo de CV reemplazado; se reintentara ruta={}", key, exception);
            }
        }
    }
}
