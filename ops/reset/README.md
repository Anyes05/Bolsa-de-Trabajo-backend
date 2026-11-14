# Reset operacional explícito (V1–V40)

**Operación destructiva: requiere autorización explícita del responsable.** No se ejecuta en deploy,
ni por Spring ni por Flyway. `reset.sql` está fuera de `classpath:db/migration`;
no agregar esa carpeta a Flyway, no ejecutar `flyway clean`, no modificar V4 aplicada.

Conserva exactamente las filas ADMIN de `usuarios`, incluidos contraseña, foto y
restantes columnas; todas las filas de `rubros_empleo` y de `flyway_schema_history`.
Vacía el resto de tablas públicas conocidas (también tarifas, ledger, idiomas,
habilidades, notificaciones de admins y comunicados). No reinicia secuencias.
Tablas desconocidas, esquema posterior a V40, ausencia de admin o protección
contable faltante/deshabilitada abortan. Los permisos/funciones/esquema permanecen.
Los triggers de TRUNCATE del ledger se deshabilitan únicamente dentro de la
transacción con bloqueo exclusivo; se restauran antes del commit. Un error revierte
también el DDL. Requiere propietario de tablas, no el rol de ejecución limitado.

## Preparación y aprobación

1. Revisar impacto y ventana sin tráfico. Identificar endpoint, puerto, **nombre de
   base**, usuario propietario y cuenta/región/bucket a partir del despliegue real.
   `application.yml` usa `S3_BUCKET`/`AWS_REGION`; compose solo reenvía variables:
   eso **no confirma** ningún bucket. Contrastar configuración efectiva de
   producción de forma privada, sin copiar secretos al repo ni imprimirla.
   No deducir el target únicamente por el nombre del bucket.
2. Desplegar la versión sin bootstrap SOCIO, o aplicar la alternativa runtime
   validada descrita abajo, antes de limpiar. En el código nuevo BOOTSTRAP_MEMBER_*
   queda ignorado permanentemente; ADMIN sigue siendo opcional e idempotente.
   V4 conserva sus seeds históricos en instalaciones nuevas, no se reejecuta.
3. Detener backend, schedulers, workers de borrado CV y demás escritores; impedir
   nuevos uploads durante **ambas fases**, incluyendo cambios de terceros al bucket.
   No usar `update-ssh-sg.ps1`. Ningún script aquí reinicia servicios.
4. Crear backup completo **del target detenido**, por ejemplo
   `pg_dump -Fc --dbname NOMBRE --file RUTA_BACKUP`. Usar libpq
   PGHOST/PGPORT/PGUSER/PGPASSFILE o PGSERVICE; no contraseñas en argumentos,
   archivos versionados o logs. Guardar backup/manifiestos fuera del repo, con ACL
   privada y cifrado. Restaurar ese backup en PostgreSQL descartable y verificar
   integridad, usuarios, fotos, rubros y ledger. Conservar evidencia.
5. Respaldar también los objetos y, si aplica, **cada versión y delete marker**
   de las claves candidatas; validar recuperación y registrar evidencia local
   no vacía. No basta respaldar BD: CVs viven en S3; fotos ADMIN son BYTEA en BD,
   fotos Google son URLs externas que no se borran. Revisar que las claves CV no
   sean compartidas con sistemas ajenos; si lo son, detener el procedimiento.

## BD: dry-run primero

### Ejecución autorizada del 14/11/2026

Completada sin respaldo por autorización expresa del responsable. Se contrastaron
la identidad AWS y los destinos BD/S3 con la configuración efectiva del contenedor.
Los identificadores concretos del entorno no se incluyen en este procedimiento.
No se modificaron reglas SSH, bucket, cifrado, acceso público ni versionado.

Se detuvo/recreó el backend con la **misma imagen** y las tres variables
`BOOTSTRAP_MEMBER_EMAIL/BPS/PASSWORD` vacías persistidas en `runtime.env`;
se comprobó previamente en el bytecode desplegado que vaciarlas evita el seed.
No quedaron otras conexiones a la BD durante la limpieza. Dry-run transaccional
con rollback y dry-run S3 revisados antes del commit; nunca se usó Flyway clean.

Resultado verificado después del reinicio:
- 177 usuarios no ADMIN eliminados (157 socios, 20 postulantes); 0 restantes.
- Las 24 tablas operativas vacías; tarifas también, sin configurar tarifa 760.
- 1 ADMIN (contraseña/foto incluidas), 30 rubros y 40 filas Flyway idénticos
  antes/después, contrastados mediante fingerprints de todas sus columnas.
- 7 triggers contables presentes y habilitados.
- 16 claves CV exactas del manifiesto eliminadas; dry-run posterior: 0 objetos.
  Bucket sin versionado, AES256 y los cuatro bloqueos de acceso público activos.
- PUT/GET con contenido comparado y DELETE de un objeto propio único realizados
  con las credenciales efectivas del backend; objeto de prueba eliminado.
- `/api/actuator/health` y `/api/sectors`: HTTP 200; Flyway en el arranque
  validó las 40 migraciones, esquema V40 actualizado sin ejecutar migraciones.

Los manifiestos operativos quedan fuera del repositorio backend y contienen claves
sensibles: protegerlos. No hay backup ni posibilidad de rollback garantizada.
No hubo commit/push ni despliegue de los cambios fuente locales.

Esta herramienta ejecuta SQL mediante **psql**, no mediante Flyway. Flyway se
utiliza para validar las migraciones existentes después de la operación. No hace
falta introducir una migración destructiva para usar este procedimiento.

### Excepción explícita sin respaldo

El comportamiento predeterminado sigue exigiendo respaldo y restauración probada.
Solo con autorización expresa para perder irreversiblemente los datos, añadir
`-AllowIrreversibleWithoutBackup` a **cada** invocación BD/S3 (incluido dry-run).
En ese modo omitir `-Backup`, `-BackupRestoreVerified` y `-ObjectBackupEvidence`;
no crear evidencia ficticia. El recibo registra `irreversibleWithoutBackup: true`
y `backupSha256: null`. Un recibo sin respaldo es rechazado por S3 salvo que su
invocación también opte explícitamente por este modo. No elimina las guardas de
target, aplicación detenida, confirmación exacta, claves ni hash del manifiesto.
No existe recuperación garantizada. En imágenes antiguas, vaciar efectivamente
`BOOTSTRAP_MEMBER_EMAIL`, `BOOTSTRAP_MEMBER_PASSWORD` y `BOOTSTRAP_MEMBER_BPS` en
runtime/Compose antes del reinicio, verificando el entorno del contenedor recreado,
en vez de desplegar una imagen nueva sin validación.

Con herramientas PostgreSQL compatibles y PowerShell:

```powershell
.\ops\reset\Reset-Database.ps1 -Database NOMBRE -Backup RUTA_BACKUP -Manifest RUTA_DRY_JSON
# Tras revisión/aprobación, backup restaurado y aplicación detenida:
.\ops\reset\Reset-Database.ps1 -Database NOMBRE -Backup RUTA_BACKUP -Manifest RUTA_COMMIT_JSON -Apply -ConfirmDatabase NOMBRE -ApplicationStopped -BackupRestoreVerified
```

El backup custom debe ser legible y declarar el mismo nombre de BD; el operador
debe verificar además host/cuenta/frescura (el nombre por sí solo no identifica un
servidor). El dry-run hace el reset dentro de una transacción y ROLLBACK; adquiere
bloqueos reales, programarlo en ventana. Manifiesto nuevo obligatorio: no sobreescribe.
El manifiesto final se escribe solo si `psql` termina correctamente e incluye
SHA256 del respaldo, `committed` y claves exactas, deduplicadas, tomadas bajo los
mismos bloqueos del reset de `cvs.ruta_archivo_cv` y
`cv_archivos_pendientes_borrado.ruta`. Rutas ajenas al formato
`postulantes/<id>/cvs/<archivo>` abortan para revisión manual, sin adivinar URLs.
Si el commit ocurrió pero falló escribir el recibo, **no reiniciar ni reejecutar a
ciegas**: recuperar el manifiesto revisado del dry-run y contrastar con backup/BD
en revisión manual. No se inventan claves por listado global.

## S3: otra fase, no transacción distribuida

```powershell
$p = @{
 Manifest='RUTA_COMMIT_JSON'; Bucket='TARGET_VERIFICADO'; DeployedBucket='TARGET_VERIFICADO'
 AccountId='CUENTA_12_DIGITOS'; Region='REGION_VERIFICADA'
 ObjectBackupEvidence='RUTA_EVIDENCIA_RESTAURACION_OBJETOS'
}
.\ops\reset\Reset-S3.ps1 @p
# Revisar manifiesto/backup y guardar SHA256 previamente:
.\ops\reset\Reset-S3.ps1 @p -Apply -ConfirmBucket TARGET_VERIFICADO -ApplicationStopped -ExpectedManifestSha256 SHA256_REVISADO
```

`DeployedBucket` es declaración explícita de la verificación independiente anterior,
no autodetección. Identidad STS, propietario esperado y acceso al bucket se validan
antes de planear. CLI AWS existente, sin instalar SDK. Perfil/rol se selecciona
externamente. Nunca cambia bucket, IAM, cifrado, public access block ni versionado.
Dry-run no hace ninguna escritura AWS. Apply exige recibo de BD **committed**.
Solo elimina coincidencias exactas de claves del manifiesto, nunca por prefijo;
el listado usa prefijo únicamente para descubrir versiones y filtra igualdad exacta.
AWS CLI pagina automáticamente (no desactivar paginación).

Sin versionado elimina el objeto exacto con precondición ETag (`--if-match`, requiere
CLI que lo soporte). Enabled/Suspended elimina explícitamente todas las versiones
y delete markers, incluida versión `null`; no deja versiones antiguas recuperables.
Permisos mínimos de la fase: STS identidad, acceso/ubicación de bucket,
GetBucketVersioning, ListBucket/ListBucketVersions y DeleteObject o
DeleteObjectVersion según estado. Retention/Object Lock, MFA Delete, denegación,
fallos de red o CLI incompatible producen error explícito; no se omiten ni se usa
bypass governance. MFA Delete exige otro procedimiento aprobado.

BD ya está comprometida: fallo S3 **no revierte BD** y puede dejar borrado parcial.
No hay reintento automático; mantener sistema detenido, corregir causa y repetir
mismo manifiesto revisado para objetos/versiones restantes. Un dry-run posterior
debe mostrar cero objetos/versiones. Para rollback usar backup de BD **y**
restauración de objetos; decidir antes de reabrir tráfico. No restaurar BD con
referencias a CVs irrecuperables. Conservar manifiesto y evidencias hasta cierre.

Verificar solo ADMIN, igualdad de rubros/historial/contraseñas/fotos, tablas
operativas vacías, Flyway validate, protección contable activa; reiniciar solamente
con autorización posterior y comprobar que bootstrap no recrea SOCIO.

## Validación reproducible

`Test-S3.ps1` usa función AWS doble, nunca la CLI real; cubre dry-run, claves
hermanas, versiones/null/delete markers, suspended, propietario, denegación y MFA.
El test JUnit `OperationalResetDatabaseTest` solo se habilita con
`-DresetDisposableDatabase=true` y exige BD local llamada `reset_verification`.
Ver instrucciones/comandos y resultados en `testing.md`.
