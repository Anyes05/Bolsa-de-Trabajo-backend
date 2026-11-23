# Testing de caja negra — Backend (API)

Fecha: 23/11/2026. Complementa `testing.md` (caja blanca / cobertura JaCoCo).

## Qué es y cómo se hizo

En las pruebas de caja negra se prueba **el comportamiento visible del sistema sin mirar el código**.
Los casos se diseñan a partir de los requerimientos (RF), del caso de uso `RF-11-bolsa-de-empleo.md`,
del `docs/SSDLC.md` y del contrato HTTP de la API, y se verifican solo entradas y salidas:
código de estado, cuerpo de la respuesta y efectos observables por otras llamadas.

- **Cómo se ejecuta:** se levanta la **aplicación completa**: Spring Boot, la seguridad real, Flyway
  y PostgreSQL. Las pruebas actúan como un cliente HTTP externo, con `java.net.http.HttpClient`.
  No llaman a clases internas ni leen la base de datos.
- **Qué se simula:** solo S3 de AWS, para no subir archivos reales. El SMTP queda sin configurar,
  igual que en un entorno sin Gmail.
- **Técnicas usadas:**
  - **PE**: particiones de equivalencia.
  - **VL**: análisis de valores límite.
  - **TD**: tabla de decisión.
  - **TE**: transición de estados.
  - **CU**: escenario de caso de uso de punta a punta.
- **Dónde está el código:** `src/test/java/uy/ccisj/api/cajanegra/`
  - `ApiCajaNegraBase.java`: levanta la app y el cliente HTTP.
  - `AutenticacionCajaNegraTest.java`
  - `ControlDeAccesoCajaNegraTest.java`
  - `BolsaDeEmpleoCajaNegraTest.java`
  - `CajaYDatosCajaNegraTest.java`
  - `PaginacionCajaNegraTest.java`

### Ejecución

Con una base PostgreSQL de prueba, la misma que usa CI:

```bash
mvn --batch-mode verify "-DtestJdbcUrl=jdbc:postgresql://127.0.0.1:5432/ccisj_test" "-DtestJdbcUser=ccisj_test" "-DtestJdbcPassword=ccisj_test"
# Solo caja negra:
mvn --batch-mode test -Dtest='*CajaNegraTest' "-DtestJdbcUrl=..."
```

Sin `-DtestJdbcUrl` estas pruebas se omiten, como el resto de pruebas con base de datos.
En CI corren automáticamente, porque el job `test` ya levanta `postgres:16`.

Particularidades de Windows:

- Si el puerto 5432 está ocupado por un PostgreSQL instalado, usar otro puerto (por ejemplo 55432).
- Exportar `JAVA_TOOL_OPTIONS=-Duser.timezone=America/Montevideo`.

### Resultado

**74 casos ejecutados, 74 aprobados**, todos en la ejecución completa de `mvn verify`:
618 tests en total, 0 fallos y 1 omitido a propósito (el reset destructivo). Cobertura de líneas
del backend con todas las pruebas: 100% (4045/4045).

## Casos de prueba

### Autenticación y registro (RF-18 / RF-19) — `AutenticacionCajaNegraTest`

| ID | Técnica | Entrada | Resultado esperado | Obtenido |
|---|---|---|---|---|
| CN-API-01 | CU, PE válida | Registro con todos los datos válidos | 201, token y rol `POSTULANTE`; `/auth/me` responde con ese correo | ✅ |
| CN-API-02 | VL | Clave de 7 / 8 / 72 / 73 caracteres | 400 / 201 / 201 / 400 | ✅ |
| CN-API-02 | PE inválidas | Clave sin mayúscula, sin minúscula, sin número o sin carácter especial | 400 en cada caso | ✅ |
| CN-API-03 | VL | 0 / 1 / 4 / 5 rubros elegidos | 400 / 201 / 201 / 400 | ✅ |
| CN-API-04 | PE inválidas | Sin aceptar términos; correo mal formado; correo ya registrado | 400; 400; 409 con un mensaje genérico que no revela si el correo existe | ✅ |
| CN-API-05 | PE | Ingreso correcto; clave incorrecta; rol equivocado; usuario inexistente; identificador vacío; sin rol | 200; 401; 401; 401; 400; 400 | ✅ |
| CN-API-06 | TE | 5 intentos fallidos desde el mismo cliente, y luego un 6.º intento (incluso con la clave correcta) | Los 5 primeros dan 401; el 6.º da 429 (bloqueado) | ✅ |
| CN-API-06b | TE, PE inválida | Los mismos 5 fallos desde un cliente, cambiando `X-Forwarded-For` en cada intento | El 6.º intento igual da 429: el encabezado falso no saltea el límite (corrección del hallazgo 1) | ✅ |
| CN-API-07 | TE | `/auth/me` → logout → `/auth/me` con el mismo token | 200 → 2xx → 401: el token queda invalidado en el servidor | ✅ |
| CN-API-08 | PE inválidas | Sin token; token mal formado; token con la firma alterada | 401 en cada caso | ✅ |

### Control de acceso por rol (SSDLC: "escalada de rol") — `ControlDeAccesoCajaNegraTest`

Tabla de decisión **rol × área**, CN-API-09 (19 combinaciones):

| Área (GET) | Anónimo | Postulante | Socio | Admin |
|---|---|---|---|---|
| `/admin/socios` | 401 | 403 | 403 | **200** |
| `/admin/caja` | 401 | — | 403 | **200** |
| `/socio/bolsa` | 401 | 403 | **200** | 403 |
| `/postulante/perfiles` | 401 | **200** | 403 | 403 |
| `/ofertas` | 401 | 403 | **200** | **200** |

Todas las combinaciones dieron el resultado esperado ✅.

| ID | Técnica | Entrada | Resultado esperado | Obtenido |
|---|---|---|---|---|
| CN-API-10 | PE | `/actuator/health` y `/sectors` sin sesión; error 403 | Las rutas públicas dan 200; el error no expone trazas ni clases internas | ✅ |

### Bolsa de empleo y postulaciones (RF-11) — `BolsaDeEmpleoCajaNegraTest`

| ID | Técnica | Entrada | Resultado esperado | Obtenido |
|---|---|---|---|---|
| CN-API-11 | CU | Flujo completo (ver detalle debajo) | Ver detalle debajo | ✅ |
| CN-API-12 | PE inválida | Postularse dos veces a la misma oferta | La 1.ª da 201; la 2.ª da 409 | ✅ |
| CN-API-13 | TE | Cerrar la oferta → postularse → reabrirla | Cerrar da 200; postularse da 409; reabrir da 409 | ✅ |
| CN-API-14 | TE | Cambios de estado de la postulación (ver tabla de transiciones) | Ver tabla de transiciones | ✅ |
| CN-API-15 | PE | Otro socio consulta, cambia el estado o descarga el CV de una postulación ajena | El listado da 200 vacío; el cambio de estado da 404; la descarga del CV da 403 o 404 | ✅ |
| CN-API-16 | VL, PE | Validación de ofertas (ver tabla de valores) | Ver tabla de valores | ✅ |
| CN-API-17 | PE inválida | Archivo con extensión `.pdf` y tipo `application/pdf` que no es un PDF real | 400; no queda ningún CV guardado (nunca 500) | ✅ |

**CN-API-11, flujo completo:**

1. El admin publica la oferta.
2. El postulante sube un PDF real a su perfil.
3. El postulante ve la oferta y se postula.
4. El socio consulta las postulaciones y descarga el CV.

Resultado esperado:

- La postulación se crea con 201.
- "Mis postulaciones" muestra 1.
- El socio la ve como `RECIBIDA`, sin correo del postulante.
- El enlace temporal del CV da 200.

**CN-API-14, transiciones de estado de la postulación:**

| Secuencia | Esperado |
|---|---|
| `RECIBIDA` → `REVISADA` → `CONTACTADA` → `SELECCIONADA` | 200, 200, 200 |
| `RECIBIDA` → `DESCARTADA` → `REVISADA` | 200, 409 |
| `RECIBIDA` → `CONTACTADA` (salta un estado) | 409 |
| `RECIBIDA` → `SELECCIONADA` | 409 |
| `RECIBIDA` → `REVISADA` → `RECIBIDA` (vuelve atrás) | 200, 409 |

**CN-API-16, validación de ofertas:**

| Campo | Valor | Esperado |
|---|---|---|
| Vacantes | 0 | 400 |
| Vacantes | 1 | 201 |
| Fecha de cierre | ayer | 400 |
| Fecha de cierre | hoy | 201 |
| Título | vacío | 400 |
| Título | 180 caracteres | 201 |
| Título | 181 caracteres | 400 |
| Descripción | vacía | 400 |

### Caja, socios y datos personales (RF-05 y relacionados) — `CajaYDatosCajaNegraTest`

| ID | Técnica | Entrada | Resultado esperado | Obtenido |
|---|---|---|---|---|
| CN-API-18 | VL, PE | Monto del cobro: −1 / 0 / 0,01 / 150,50 / 1,005 | 400 / 400 / 2xx / 2xx / 400 (más de 2 decimales) | ✅ |
| CN-API-19 | PE inválidas | Cobro sin método de pago; cobro a un socio inexistente | 400; 404 | ✅ |
| CN-API-20 | PE | Alta de socio válida; identificador repetido; sin datos obligatorios | 201, con una clave provisional de 12 caracteres o más que no contiene la cédula; 409; 400 | ✅ |
| CN-API-21 | PE | Datos personales válidos; cada campo obligatorio vacío; cédula de otro postulante | 200; 400 en cada campo; 409 (nunca 500) | ✅ |

### Listados paginados (búsqueda, filtros y paginación en el servidor) — `PaginacionCajaNegraTest`

| ID | Técnica | Entrada | Resultado esperado | Obtenido |
|---|---|---|---|---|
| CN-API-22 | VL | Tamaño de página 0 / 1 / 100 / 101 | 400 / 200 / 200 / 400 | ✅ |
| CN-API-23 | VL, PE inválidas | Página −1; búsqueda de 100 y de 101 caracteres; `page=uno`; `estado` y `libreta` con valores fuera del catálogo | 400; 200 / 400; 400 sin repetir el valor recibido en el mensaje; 400 | ✅ |
| CN-API-24 | PE | Buscar un socio por razón social; pedir una página fuera de rango | Encuentra exactamente 1; la página viene vacía pero informa el total | ✅ |
| CN-API-25 | PE | Postulantes del admin con `estado` TODOS / ACTIVO / INACTIVO y búsqueda por correo | 200 en cada estado; la búsqueda devuelve 1 perfil; filtrando INACTIVO, 0 | ✅ |
| CN-API-26 | PE | Bolsa del socio ordenada por nombre o recientes, con filtros de rubro, disponibilidad y libreta; orden inválido | 200 con página en cada combinación; orden inválido da 400 | ✅ |
| CN-API-27 | PE, TD | Exportación de Caja sin estados y con todos los estados; oferta por id como admin, socio dueño, otro socio y anónimo | Vacía / encuentra al socio; 200, 200, 404, 401 | ✅ |

## Hallazgos

1. **Se podía saltear el límite de intentos de ingreso.** Estado: **corregido** (23/11/2026).
   - **Qué pasaba:** `AuthRateLimitService.clientIp` identificaba al cliente con el **primer** valor
     del encabezado `X-Forwarded-For`, y ese valor lo puede escribir cualquier cliente. Nginx usa
     `$proxy_add_x_forwarded_for`, que agrega la IP real al final pero conserva lo que mandó el cliente.
   - **Impacto:** cambiando ese encabezado en cada intento se salteaba el límite de 5 intentos cada
     15 minutos (protección contra fuerza bruta del SSDLC) y el de 10 registros.
   - **Corrección:** el backend ya no usa `X-Forwarded-For`. Usa `X-Real-IP`, que el Nginx del host
     fija con `$remote_addr` (no lo puede alterar el cliente), y solo si el pedido llega desde el
     proxy propio (loopback o red privada). Desde cualquier otro origen usa la IP de la conexión.
     No hizo falta cambiar Nginx.
   - **Verificación:** CN-API-06b (caja negra) y `AuthRateLimitServiceTest` (unitarias).

2. **La salud del servicio dependía del correo.** Estado: **corregido** (23/11/2026).
   - **Qué pasaba:** `/actuator/health` respondía 503 si el SMTP no estaba disponible, porque Spring
     incluía el chequeo de correo. El deploy verifica ese endpoint, así que una caída de Gmail podía
     hacer fallar el deploy aunque la aplicación funcionara.
   - **Corrección:** `management.health.mail.enabled: false` en `application.yml`. El correo es
     best-effort; la base de datos y el disco se siguen chequeando.
   - **Verificación:** CN-API-10 corre sin SMTP y espera 200.

3. **El listado de postulaciones de una oferta ajena responde 200 vacío.** Estado: aceptado.
   - **Qué pasa:** un socio que consulta las postulaciones de una oferta que no es suya recibe 200
     con una lista vacía, en lugar de 403 o 404.
   - **Impacto:** no filtra datos; el comportamiento queda documentado en CN-API-15.

Los errores 500 por campos vacíos y por cédula duplicada en los datos personales se encontraron y
corrigieron antes de esta suite (ver `testing.md`). CN-API-21 verifica que no volvieron a aparecer.

## Otras pruebas de caja negra del proyecto

- **Frontend:** pruebas E2E con Playwright en un navegador real. Ver `testing-caja-negra.md` en
  el repo del frontend.
- **Scripts operativos:** `ops/reset/Test-Guards.ps1` y `ops/reset/Test-S3.ps1` prueban los scripts
  de reset desde afuera: ejecutan, verifican las salidas y comprueban que se rechacen los usos
  peligrosos.
