# Plan de testing del proyecto final — CCISJ

Fecha de referencia: 11/11/2026.

## 1. Objetivo y ubicación del plan

Completar la etapa de testing del Sistema de Gestión Integrado del Centro Comercial e Industrial de San José mediante una pirámide de pruebas, priorizando reglas de negocio y flujos de demostración.

Este es el **plan principal**, ubicado en backend porque concentra las reglas, las migraciones, la persistencia, los jobs y el envío de correos. El frontend mantiene su [checklist específico](https://github.com/Anyes05/Bolsa-de-Trabajo-frontend/blob/master/testing.md), sin duplicar este plan.

Tener ambos documentos dentro de sus repositorios permite versionarlos y consultarlos independientemente. No se recomienda guardar el único plan en la carpeta superior del proyecto, que no pertenece a ninguno de los dos repositorios.

**Este documento distingue automatización incorporada de trabajo pendiente: no certifica que todo esté probado.** Las casillas se marcan solamente al adjuntar evidencia reproducible.

## 2. Punto de partida

El avance comunicado inicialmente por el equipo indicaba los RF como implementados. Al contrastar el listado detallado con ambos repositorios se encontraron requisitos parciales y diferencias de alcance: **no corresponde afirmar cumplimiento total del documento original**. Se reportaron verificaciones funcionales de:

- Autenticación y roles; login, registro y vínculo con Google para postulantes.
- Comunicados por campana y correo, con pop-ups bloqueantes.
- Aniversarios automáticos con job diario, notificación y correo.
- Retención tras dos meses de inactividad: aviso, gracia, ocultamiento y confirmación de permanencia.
- Plantillas HTML de correos con identidad CCISJ para credenciales, comunicados, aniversario, permanencia y selección.

Las verificaciones E2E reportadas en producción son antecedentes; **no equivalen a una suite Playwright automatizada y repetible**.

Base técnica observada:

| Área | Situación |
|---|---|
| Backend | Existen pruebas JUnit de autenticación, Google, saldo, morosidad, plantillas, notificaciones, fotos e importación. |
| Persistencia | Existen pruebas con PostgreSQL aislado para fotos, disponibilidad, CV único, suscripciones, cobros de inactivos y reserva del nombre de empresa. Requieren activación explícita. |
| Frontend | Existen Vitest para filtros de Caja y campos ficticios, además de pruebas Node en `tests/`. |
| CI backend | El job `test` ejecuta `mvn --batch-mode clean verify`. |
| CI frontend | Workflow reutilizable con Vitest, Node, tipos/build y smoke público Playwright; publicación de imagen condicionada a tests. Ejecución remota pendiente. |
| Playwright y GreenMail | GreenMail incorporado a las pruebas JUnit del backend. Playwright incorporado para UI pública escritorio/móvil; siguen pendientes los ocho E2E de stack real. |

No usar una ejecución exitosa con pruebas de base de datos omitidas como evidencia de integración completa.

### 2.1. Relevamiento de RF y RNF contra el código

Referencia de revisión: backend `fbfbff5`, frontend `49cb966`, más este documento. Se revisaron fuentes, pantallas, servicios, migraciones, dependencias, configuración y workflows de ambos repositorios; no se inspeccionó AWS en vivo en este relevamiento.

Estados:

- **Implementado:** existe un recorrido identificable; no significa prueba aprobada.
- **Parcial / divergente:** hay una base utilizable, pero no cumple literalmente todo el requisito.
- **No encontrado:** no se halló implementación en los repositorios revisados; no se da por existente.
- **Por verificar:** requiere medición, evidencia del despliegue o validación externa.

#### Requerimientos funcionales

| RF | Estado observado | Qué probar ahora / diferencia pendiente | Evidencia principal |
|---|---|---|---|
| RF-01 | Implementado con ampliación de identificación | CRUD con baja lógica, campos del socio, fechas y directivo. El sistema admite BPS **o cédula** excluyentes, no BPS obligatorio para todos. Probar ambos identificadores y duplicados. | [SocioService](src/main/java/uy/ccisj/api/socio/SocioService.java), [V24](src/main/resources/db/migration/V24__socio_cedula_xor_bps.sql). |
| RF-02 | Parcial | Existen monto, medio, cobros y reintegros puntuales/recurrentes. La fecha del cobro se asigna con `LocalDate.now()`; no existe elección manual de fecha en el DTO. No planificar un selector de fecha de pago como función disponible. | [CajaService](src/main/java/uy/ccisj/api/socio/CajaService.java), [RegistrarCobroDTO](src/main/java/uy/ccisj/api/socio/dto/RegistrarCobroDTO.java). |
| RF-03 | Parcial / semántica divergente | Hay filtros y colores. “Con deuda” usa saldo positivo; “Al día” usa ausencia de deuda vencida y puede tener cargos aún no vencidos, por lo que no equivale siempre a cuenta saldada. Probar esa diferencia y resolver la redacción del RF. | [CajaService](src/main/java/uy/ccisj/api/socio/CajaService.java), [SaldoCuenta](src/main/java/uy/ccisj/api/socio/SaldoCuenta.java), frontend `src/utils/cajaFilters.ts`. |
| RF-04 | Parcial; recargos excluidos por el código | Existe tarifa por año y emisión mensual. **No hay recargos automáticos**: las reglas declaran expresamente “sin recargos por mora: decisión de producto”. Probar cuota/saldo/morosidad sin intereses; registrar la exclusión como decisión por aprobar contra el RF. | [TarifaService](src/main/java/uy/ccisj/api/socio/TarifaService.java), [MorosidadRules](src/main/java/uy/ccisj/api/socio/MorosidadRules.java). |
| RF-05 | Parcial respecto de inmutabilidad estricta | Historial cronológico de cargos/pagos/reintegros y saldos calculados, sin endpoints de edición/borrado de pagos. No se encontró una bitácora técnica append-only con restricciones/triggers que garantice inmutabilidad ante escrituras directas a BD. Probar preservación por los flujos de aplicación, no certificar auditoría inviolable. | [CajaController](src/main/java/uy/ccisj/api/socio/CajaController.java), [CajaService](src/main/java/uy/ccisj/api/socio/CajaService.java), [migraciones](src/main/resources/db/migration). |
| RF-06 | Parcial | Importa `.xls`/`.xlsx`, no CSV. Exporta padrón completo y Caja con selección/estados/fechas propios del modal. El modal reinicia sus filtros al abrir: no hereda automáticamente el filtro activo del listado. Probar lo existente y registrar ambas diferencias. | [SocioImportService](src/main/java/uy/ccisj/api/socio/importacion/SocioImportService.java), frontend `AdminDashboardView.vue`, `sociosExcelService.ts`, `cajaExcelService.ts`. |
| RF-07 | Implementado | Alta, edición y baja/reactivación lógica de rubros; duplicados y permisos ADMIN. | [RubroAdminService](src/main/java/uy/ccisj/api/catalogo/RubroAdminService.java). |
| RF-08 | Implementado | Registro, datos personales y CV manual generado como PDF o archivo cargado; persistencia y recuperación. | [AuthService](src/main/java/uy/ccisj/api/auth/AuthService.java), [CvService](src/main/java/uy/ccisj/api/postulante/CvService.java), frontend `PostulantePerfilesView.vue`. |
| RF-09 | Parcial / modelo distinto al texto | Múltiples perfiles y un CV máximo por perfil, almacenado en S3. Un perfil admite hasta **4 rubros**, no exactamente uno; registro y edición aplican ese límite por perfil. No confundirlo con límite global de perfiles. Probar modelo actual; aclarar “sin límite de rubros” contra esta regla. | [PerfilLaboral](src/main/java/uy/ccisj/api/postulante/PerfilLaboral.java), [PostulantePerfilController](src/main/java/uy/ccisj/api/postulante/PostulantePerfilController.java), [RegisterApplicantRequest](src/main/java/uy/ccisj/api/auth/RegisterApplicantRequest.java), [V34](src/main/resources/db/migration/V34__single_cv_per_profile.sql). |
| RF-10 | Implementado | Alta/edición/pausa/cierre por SOCIO titular y ADMIN; fecha exacta o indefinida y vencimiento. Incluir reserva opcional del nombre de empresa, visible por defecto. | [OfertaEmpleoService](src/main/java/uy/ccisj/api/oferta/OfertaEmpleoService.java). |
| RF-11 | Parcial / política a aclarar | Búsqueda, filtro, consulta de CV y selección de perfil al postular existen. El acceso a Bolsa exige socio activo, **no exige estar al día**: un moroso activo puede acceder. No escribir una prueba que espere bloqueo por mora como si ya existiera. | [SocioBolsaController](src/main/java/uy/ccisj/api/socio/SocioBolsaController.java), [MorosidadRules](src/main/java/uy/ccisj/api/socio/MorosidadRules.java), [PostulacionService](src/main/java/uy/ccisj/api/oferta/PostulacionService.java). |
| RF-12 | Implementado | Vista ADMIN de postulantes, incluidos pausados, y gestión de ofertas desde administración. | [AdminPostulanteController](src/main/java/uy/ccisj/api/postulante/AdminPostulanteController.java), [OfertaEmpleoController](src/main/java/uy/ccisj/api/oferta/OfertaEmpleoController.java). |
| RF-13 | Implementado | Retención por dos meses, aviso, gracia, ocultamiento y confirmación; probar tiempo y persistencia sin esperar meses reales. | [RetencionPermanenciaService](src/main/java/uy/ccisj/api/postulante/RetencionPermanenciaService.java), [scheduler](src/main/java/uy/ccisj/api/postulante/RetencionPermanenciaScheduler.java). |
| RF-14 | Implementado | Comunicados a socios activos o directivos activos por campana + correo. El envío SMTP sucede después de persistir: probar reporte de fallos parciales, no asumir rollback de toda la difusión. Audiencia de postulantes no habilitada en este flujo. | [ComunicadoService](src/main/java/uy/ccisj/api/comunicado/ComunicadoService.java). |
| RF-15 | Implementado | Aniversario por fecha específica, no por alta; campana y envío SMTP best-effort, deduplicación y 29 de febrero. | [AniversarioService](src/main/java/uy/ccisj/api/socio/AniversarioService.java), [scheduler](src/main/java/uy/ccisj/api/socio/AniversarioScheduler.java). |
| RF-16 | Implementado mediante acción explícita | Leer notificaciones y comunicar selección/no selección. El socio usa la acción de notificar/contactar: cambiar solo el estado de una candidatura no equivale a enviar ese correo. Probar empresa real incluso en oferta reservada. | [SocioBolsaController](src/main/java/uy/ccisj/api/socio/SocioBolsaController.java), [NotificacionService](src/main/java/uy/ccisj/api/notificacion/NotificacionService.java). |
| RF-17 | Implementado con cierre adicional | Pop-up bloqueante: no cerrar por Escape/fondo; permite X y botón de confirmación/cierre. No exigir exclusivamente X sin acordar ese ajuste de UX. | Frontend `src/components/socio/SocioShell.vue`, [NotificacionService](src/main/java/uy/ccisj/api/notificacion/NotificacionService.java). |
| RF-18 | Parcial | Fotos para los tres roles. ADMIN: foto/contraseña, correo de acceso solo lectura. SOCIO: datos de empresa, contacto y contraseña. POSTULANTE: datos/foto y formulario de cambio de contraseña local con clave actual, nueva y confirmación; correo deshabilitado. Cuentas creadas solo con Google mantienen ingreso Google; no se agregó recuperación/creación de clave sin clave actual. No hay silenciado de correo ni descripción genérica editable en todos los perfiles. | [ProfilePhotoService](src/main/java/uy/ccisj/api/auth/ProfilePhotoService.java), [AuthService](src/main/java/uy/ccisj/api/auth/AuthService.java), [User](src/main/java/uy/ccisj/api/user/User.java); frontend `AdminPerfilView.vue`, `SocioPerfilView.vue`, `PostulanteMiPerfilView.vue`. |
| RF-19 | Implementado con ampliación | Selección de rol, acceso socio por BPS/cédula, correo/clave para otros roles y Google para postulantes; logout en shells. Correo válido no está restringido globalmente a dominio Gmail/institucional: no presumir esa validación. | [AuthService](src/main/java/uy/ccisj/api/auth/AuthService.java), [GoogleAuthService](src/main/java/uy/ccisj/api/auth/GoogleAuthService.java), frontend `SessionView.vue` y shells. |
| RF-20 | Implementado | Baja desactiva usuario; obligación y plazo de 24 h para cambio de clave provisional; comprobar también token previo a la baja y bloqueo de rutas antes del cambio. | [SocioService](src/main/java/uy/ccisj/api/socio/SocioService.java), [AuthService](src/main/java/uy/ccisj/api/auth/AuthService.java), [JwtAuthenticationFilter](src/main/java/uy/ccisj/api/auth/JwtAuthenticationFilter.java). |
| RF-21 | Implementado con fallo explícito | Alta intenta enviar credenciales; si SMTP falla, el socio puede quedar creado con resultado de envío fallido y opción de reenvío. Probar este contrato; no exigir recepción garantizada por el alta. | [SocioService](src/main/java/uy/ccisj/api/socio/SocioService.java). |
| RF-22 | Implementado | Gestión de Socios busca por razón social, BPS, RUT y otros datos; Caja mantiene su búsqueda fiscal. No confundir ambos buscadores. | Frontend `src/views/admin/AdminSociosView.vue`, `src/utils/cajaFilters.ts`. |
| RF-23 | Implementado | Pausa global persistente; no elimina cuenta, oculta en búsquedas de socios y conserva visibilidad ADMIN. | [PostulanteDisponibilidadController](src/main/java/uy/ccisj/api/postulante/PostulanteDisponibilidadController.java), [SocioBolsaController](src/main/java/uy/ccisj/api/socio/SocioBolsaController.java), [AdminPostulanteController](src/main/java/uy/ccisj/api/postulante/AdminPostulanteController.java). |
| RF-24 | Parcial en “listado completo” | Permiso exclusivo de directivos validado por backend. El directorio filtra usuarios activos: no lista todos los socios de baja. Probar denegación al estándar y listado actual; resolver si “completo” incluye inactivos. Esto no certifica cumplimiento legal integral. | [SocioService.listDirectorio](src/main/java/uy/ccisj/api/socio/SocioService.java), [SocioDirectorioController](src/main/java/uy/ccisj/api/socio/SocioDirectorioController.java). |

Las referencias frontend de esta tabla corresponden al [repositorio frontend](https://github.com/Anyes05/Bolsa-de-Trabajo-frontend/tree/master/src); sus diferencias principales también figuran en su `testing.md`.

#### Requerimientos no funcionales

| RNF | Estado observado | Alcance de testing real / brecha | Evidencia |
|---|---|---|---|
| RNF-01 | Implementado con ampliación | IDs autogenerados y unicidad de BPS; también se admite cédula única excluyente. Validar restricciones reales, no exigir BPS para socios registrados con cédula. | [V1](src/main/resources/db/migration/V1__initial_auth_schema.sql), [V24](src/main/resources/db/migration/V24__socio_cedula_xor_bps.sql), [User](src/main/java/uy/ccisj/api/user/User.java). |
| RNF-02 | Implementado; responsive por verificar visualmente | Vue 3, Java 21/Spring Boot, REST/JPA/Hibernate/Flyway. Revisar build/migraciones y escritorio/móvil. | [pom.xml](pom.xml), [application.yml](src/main/resources/application.yml), frontend `package.json`, `src/assets/css/app.css`. |
| RNF-03 | Parcial | Spring Security, BCrypt, JWT y Google con validación de ID token existen. No se usa un flujo configurado de Spring `oauth2Login`; probar la implementación Google real. No se encontraron SAST/DAST en los workflows ni páginas/registro de aceptación de términos/privacidad. HTTPS forzado y política TLS no quedan demostrados por el repo: Nginx del contenedor escucha HTTP 80; un proxy externo podría terminar TLS. Verificar despliegue con autorización. No certificar la Ley 18.331 solo con pruebas técnicas. | [SecurityConfig](src/main/java/uy/ccisj/api/auth/SecurityConfig.java), [GoogleTokenVerifier](src/main/java/uy/ccisj/api/auth/GoogleTokenVerifier.java), [pom.xml](pom.xml), [workflow backend](.github/workflows/ci-cd.yml); frontend `nginx.conf`, workflows y rutas. |
| RNF-04 | Integración/configuración existente; despliegue por verificar | Docker, GitHub Actions, despliegue EC2/ECR, referencia a RDS y servicio S3 presentes. La salud, permisos y configuración actual de AWS requieren evidencia del entorno. Frontend test y deploy son workflows separados: no hay dependencia explícita del deploy sobre el resultado del workflow test. | [Dockerfile](Dockerfile), [workflow backend](.github/workflows/ci-cd.yml), [S3StorageService](src/main/java/uy/ccisj/api/storage/S3StorageService.java); frontend `Dockerfile`, `.github/workflows/deploy-ecr.yml`, `.github/workflows/test.yml`. |
| RNF-05 | SMTP implementado; Gmail API no | `JavaMailSender` hacia SMTP Gmail con STARTTLS. **SMTP y Gmail API no son lo mismo**; no hay cliente Gmail API de envío. Probar SMTP con GreenMail y recepción Gmail manual; corregir la redacción o tratar Gmail API como desarrollo futuro si es obligatorio. | [MailNotificationService](src/main/java/uy/ccisj/api/mail/MailNotificationService.java), [application.yml](src/main/resources/application.yml), [pom.xml](pom.xml). |
| RNF-06 | Parcial | Identidad CCISJ y diseño claro responsive existen. No se encontró selector ni tema oscuro. El CSS define primero verde `#008d45`/Source Sans 3 y luego sobreescribe variables con `#078747`/Manrope, además de fuente de títulos; no coincide literalmente con Inter/Roboto ni los verdes pedidos. `--color-brand-dark` es un tono de marca, no modo oscuro. Probar UI clara actual; reservar modo oscuro/fidelidad exacta como brecha de alcance. | Frontend `src/assets/css/app.css`, componentes y vistas. |
| RNF-07 | No acreditado, requiere medición | No se encontró benchmark automatizado que demuestre **tiempos estrictamente menores a 2 s** para filtrado de Caja, consultas contables y catálogo. No es una función inexistente: los tres flujos existen y sí deben medirse. | [CajaService](src/main/java/uy/ccisj/api/socio/CajaService.java), [SocioBolsaController](src/main/java/uy/ccisj/api/socio/SocioBolsaController.java), tests y frontend `AdminDashboardView.vue`. |

### 2.2. Fuera de las pruebas de aceptación actuales

No crear casos que pretendan usar estas funciones como si estuvieran listas:

- Recargos automáticos por mora.
- Elección manual de fecha del cobro.
- Importación CSV.
- Silenciar notificaciones por correo.
- Edición universal de descripción/correo/contraseña en la UI de perfil de los tres roles.
- Modo oscuro y cambio claro/oscuro.
- Envío mediante Gmail API.
- Bitácora técnica inviolable/append-only garantizada por BD.
- Pantallas de términos/privacidad y aceptación persistida no encontradas.
- Ejecución de SAST/DAST como controles ya conectados a CI.

Estas ausencias **no son aprobaciones ni exclusiones automáticas de los requisitos**. Registrar una brecha por cada una: implementar antes de la aceptación o aprobar por escrito un cambio de alcance con el equipo/tutor/cliente. Una prueba negativa que confirme ausencia o rechazo puede documentar la brecha, pero no constituye prueba de la funcionalidad pendiente.

La infraestructura nueva de pruebas (Playwright, GreenMail, fixtures y reportes) sí pertenece al trabajo de testing propuesto; no debe confundirse con funcionalidades de producto inexistentes.

Decisiones pendientes antes de fijar resultados esperados:

- [ ] Ratificar la exclusión de recargos y documentar el modelo real de cuota anual parametrizada con emisión mensual.
- [ ] Acordar si “Al día” significa saldo cero o ausencia de deuda vencida, y si Bolsa se restringe a no morosos.
- [ ] Aclarar BPS/cédula y el modelo de hasta cuatro rubros por perfil frente al texto original.
- [ ] Acordar filtros propios del modal Excel frente a heredar los del listado y directorio de activos frente a todos los socios.
- [ ] Resolver las brechas de RF-18 y RNF-03/RNF-06 sin declararlas probadas por otras funciones.
- [ ] Corregir “SMTP (Gmail API)” para distinguir protocolos y alcance de integración.

## 3. Estrategia: pirámide por fases

1. **Unitarias, mayoría:** reglas pequeñas, rápidas y deterministas.
2. **Integración/API:** contratos HTTP, permisos, transacciones, PostgreSQL, migraciones y SMTP capturado.
3. **E2E smoke:** pocos recorridos de alto valor con navegador, frontend y backend reales.
4. **Manual/UAT:** servicios externos y aceptación del usuario, con evidencia.

Prioridades: **P0** bloquea la entrega; **P1** completa la regresión funcional; **P2** profundiza la evaluación.

### Automatización primero y asistencia de IA

Objetivo: automatizar toda verificación repetible que aporte evidencia del requisito, dejando intervención humana para aceptación y comprobaciones externas. No fijar un porcentaje de automatización o cobertura sin inventariar primero los casos; un porcentaje alto no demuestra cumplimiento de la letra.

| Bloque | Automatización propuesta | Intervención humana residual |
|---|---|---|
| Reglas de negocio | JUnit/Vitest/Node con casos parametrizados, límites temporales, importes y permisos. | Validar resultados esperados contra RF y decisiones aprobadas, no solamente contra la implementación. |
| Persistencia/API | PostgreSQL descartable + Flyway, fixtures, HTTP real, concurrencia y consultas de comprobación tras cada operación. | Revisar contratos y divergencias detectadas. |
| Correos | GreenMail: destinatarios, cantidad, asunto, HTML/texto, enlaces y fallos SMTP controlados. | Comprobar recepción/renderizado/spam en Gmail real con cuentas autorizadas. |
| Jobs | Reloj/fecha controlados y disparo del servicio; verificar BD, notificaciones y correo, incluida idempotencia. | Una comprobación del scheduler desplegado y su zona horaria en entorno autorizado. |
| Excel | Generar/descargar y leer el archivo con bibliotecas: hojas, encabezados, filas, tipos, fechas, importes y totales. | Abrir una muestra en una aplicación de planillas para compatibilidad visual. |
| Navegación y UI | Playwright con stack real, tres roles, recarga/nueva sesión, errores, doble clic, tamaños de pantalla y teclado. | Aceptación visual/usabilidad y casos de proveedor Google real. |
| Rendimiento | Escenarios reproducibles para RNF-07, muestras y aserciones contra < 2000 ms; conservar resultados del entorno usado. | Revisar representatividad y límites de la conclusión, sin extrapolar a cargas no ensayadas. |
| Evidencia | Reportes JUnit, cobertura y trazas/capturas Playwright con commits y entorno, publicados por CI. | Aprobar incidencias, cambios de alcance y aceptación final. |

Flujo de trabajo asistido por IA para cada requisito:

1. Identificar RF/RNF, evidencia de código y criterio de aceptación; separar función existente de brecha.
2. Derivar casos normales, negativos, límites y permisos. Revisar especialmente importes, fechas y resultados esperados antes de generar código.
3. Generar con IA pruebas, fixtures sintéticos y configuración mínima reutilizando las herramientas existentes.
4. Ejecutar realmente las pruebas; la IA puede interpretar fallos y proponer correcciones. No aprobar por lectura del código ni por un reporte redactado sin ejecución.
5. Si un fallo revela incumplimiento, registrarlo y resolver su alcance: no cambiar la aserción para imitar el bug, omitir el caso ni añadir reintentos para conseguir verde.
6. Conservar evidencia y relacionarla con el requisito y commits ensayados. Revisar la contribución de IA y documentarla según las reglas académicas aplicables; la aceptación sigue siendo responsabilidad del equipo.

Las aserciones deben comprobar resultados observables e independientes: importes esperados calculados para el caso, filas persistidas, respuestas HTTP, destinatarios capturados y contenido descargado. No usar la misma función productiva para calcular el esperado que se intenta verificar. Los mocks son válidos en unitarias/UI, pero no acreditan integración real.

Orden para aprovechar el tiempo:

- [ ] Primero consolidar CI y activar las suites existentes, incluidas las PostgreSQL hoy opt-in, antes de duplicar pruebas.
- [ ] Después automatizar API/SMTP/jobs/Excel: cubren muchos casos con menor mantenimiento que un recorrido de navegador por cada variante.
- [ ] Agregar los ocho smoke Playwright de este plan y ampliar solo para riesgos que no estén cubiertos por capas inferiores.
- [ ] Automatizar mediciones RNF-07 y la generación de evidencia; repetir todo ante cambios.
- [ ] Si se mantiene SAST/DAST en el alcance, configurar y ejecutar esos controles antes de declararlos cumplidos. DAST solo contra un entorno de prueba autorizado.
- [ ] Reservar una sesión UAT final y una lista corta de verificaciones externas; no repetir manualmente toda la regresión en cada entrega.

## 4. Fase 1 — Consolidar la base iniciada

### Backend — P0

- [ ] Inventariar las pruebas existentes, ejecutar la base y registrar aprobadas, fallidas y omitidas.
- [ ] Completar login por rol: credenciales incorrectas, cuenta de baja, socio moroso, cambio de contraseña y vencimiento de credenciales provisionales, respetando las reglas acordadas.
- [ ] En perfil, probar solo los campos y acciones habilitados por rol según RF-18; incluir cambio de contraseña local del postulante y no esperar silenciado de correo aún inexistente.
- [ ] Verificar saldo, pagos parciales/totales, anticipos, saldo a favor, vencimientos y límites de mes/año.
- [ ] Verificar que un inactivo con deuda pueda pagar sin reactivarse, y que un inactivo sin deuda no pueda recibir cobros.
- [ ] Verificar morosidad sin confundirla con estado de suscripción.
- [ ] Cubrir plantillas de correo: contenido esperado, empresa/oferta correctas, enlaces, HTML escapado y versión de texto.
- [ ] Revisar pruebas de Google con tokens/verificador simulados; no depender de una cuenta real en unitarias.

### Frontend — P0

- [ ] Completar el [checklist del frontend](https://github.com/Anyes05/Bolsa-de-Trabajo-frontend/blob/master/testing.md).
- [ ] Mantener “Con deuda” por saldo positivo, incluyendo inactivos; excluir saldo cero y a favor.
- [ ] Ejecutar tanto Vitest como los tests Node, y compilar para producción.

### CI y trazabilidad — P0

#### Backend: automatización implementada y validación local (2026-11-11)

- El job `test` de `.github/workflows/ci-cd.yml` ejecuta `mvn clean verify` con un servicio `postgres:16` descartable y health check. Las seis suites `*DatabaseTest` se habilitan con una única configuración: `testJdbcUrl`, `testJdbcUser`, `testJdbcPassword`. Cada suite conserva su esquema aleatorio y sus migraciones/fixtures. No usa RDS ni secretos de producción.
- `migrate` conserva `needs: test` y `deploy` conserva `needs: [test, migrate]`. Los reportes `target/surefire-reports/` se publican con `if: always()`, incluso si Maven falla.
- GreenMail 2.1.3 es dependencia exclusiva de test. `MailNotificationServiceIntegrationTest` usa SMTP real capturado en loopback y puerto efímero, con autenticación y tiempos máximos de conexión/lectura/escritura: cinco pruebas de texto UTF-8, remitente/destinatario/Reply-To/asunto, HTML + alternativa de texto y enlace, envío al administrador, deduplicación, validaciones sin envío y error SMTP de autenticación convertido a HTTP 502.
- Validación local real con Java 21 y PostgreSQL **16.15 en Docker**: `mvn --batch-mode verify` con las propiedades anteriores y `-Duser.timezone=UTC` aprobó **99 pruebas, 0 fallos, 0 errores, 0 omitidas**, incluido empaquetado. Las seis suites PostgreSQL y las cinco pruebas SMTP se ejecutaron. Una primera ejecución detectó incompatibilidad del alias local `America/Buenos_Aires` con el servidor; usar UTC resolvió la conexión sin modificar código productivo.
- Validación local sin opt-in: `mvn --batch-mode verify` también aprobó el empaquetado, con **98 casos reportados: 68 ejecutados y 30 omitidos** (las seis suites PostgreSQL). No requiere una conexión de BD; GreenMail se ejecuta igualmente.
- El workflow está configurado, pero no se ejecutó en GitHub remoto. SMTP capturado no acredita recepción en Gmail, STARTTLS del proveedor, flujos transaccionales de comunicados/jobs ni E2E full-stack; esos puntos siguen pendientes.

Ejecución local opt-in (PowerShell, desde backend, solo contra una base **descartable** con permisos de creación de esquemas y `pgcrypto`):

```powershell
mvn --batch-mode verify "-Duser.timezone=UTC" "-DtestJdbcUrl=jdbc:postgresql://127.0.0.1:5432/ccisj_test" "-DtestJdbcUser=ccisj_test" "-DtestJdbcPassword=ccisj_test"
```

Los nombres anteriores por suite (`photoTestJdbcUrl`, `rf23TestJdbcUrl`, `singleCvTestJdbcUrl`, `cajaTestJdbcUrl`, `subscriptionTestJdbcUrl`, `offerVisibilityTestJdbcUrl`) se reemplazan por `testJdbcUrl`. Usuario/contraseña predeterminados: `ccisj_test` / `ccisj_test`, exclusivamente para esta base desechable. Sin `testJdbcUrl`, `mvn verify` conserva la ejecución local opt-in: omite las seis suites de BD y ejecuta el resto, incluido GreenMail, sin requerir Docker ni proveedor SMTP externo.

- [ ] Ejecutar pruebas y build en pull requests de ambos repositorios.
- [ ] Evitar que una falla de tests permita continuar con publicación/despliegue.
- [ ] Publicar reportes de pruebas; agregar cobertura y definir un umbral inicial a partir de una medición real.
- [ ] Crear matriz RF → casos de prueba → resultado → evidencia; confirmar cobertura de todos los RF, no solo de los cambios recientes.
- [ ] Asignar responsable y fecha a cada bloque pendiente.

**Salida de fase:** base de pruebas verde, sin omisiones no explicadas y con las reglas críticas identificadas.

## 5. Fase 2 — Integración/API y E2E smoke

### Entorno aislado — P0

- [ ] Crear configuración de testing sin credenciales ni endpoints de producción.
- [ ] Levantar PostgreSQL de prueba en Docker/GitHub Actions y ejecutar Flyway sobre una base descartable.
- [ ] Crear fixtures sintéticos para ADMIN, SOCIO activo/moroso/inactivo y POSTULANTE, con y sin deuda/CV/postulaciones.
- [ ] Iniciar GreenMail como servidor SMTP de pruebas y configurar el backend para enviar exclusivamente a ese servidor.
- [ ] Aislar archivos/fotos/CV con almacenamiento de prueba o dobles controlados, nunca con el bucket de producción.
- [ ] Configurar jobs con reloj controlable o disparo de prueba; no esperar días ni cambiar el reloj del servidor.
- [ ] Adaptar en el perfil SMTP de pruebas autenticación, STARTTLS y confianza TLS al servidor GreenMail; no reutilizar ciegamente la confianza fija de Gmail.
- [ ] Preparar arranque y health checks de backend/frontend, limpieza y repetibilidad.
- [ ] Habilitar explícitamente en CI las suites PostgreSQL existentes; registrar fallos y pruebas omitidas.

### Integración/API — P0/P1

- [ ] Probar HTTP real: validaciones, códigos de estado, sesión y autorización; negar acceso entre roles y entre socios propietarios distintos.
- [ ] Cobros: persistencia, deuda restante, histórico, doble envío concurrente, rollback y baja preservada.
- [ ] Suscripciones: alta/baja/reingresos, fecha manual, programación futura, cancelación, ejecución única y conservación de pagos/deudas.
- [ ] Ofertas: alta, edición, publicación, pausa, cierre y vencimiento; titularidad y restricciones.
- [ ] Nombre de empresa: visible por defecto, reservado en ofertas/Mis postulaciones cuando corresponda y real en notificaciones/correos.
- [ ] Postulación: perfil propio visible con CV, duplicados, oferta cerrada y respuesta persistida.
- [ ] CV único: carga manual/archivo, reemplazo confirmado, borrado y referencias de postulaciones.
- [ ] RF-23: pausa persistida, ocultamiento para socios y visibilidad para ADMIN, sin perder configuraciones individuales.
- [ ] Fotos de los tres roles: guardar, recuperar tras nueva sesión y eliminar.
- [ ] Comunicados y selección/no selección: capturar destinatario, asunto, HTML/texto y notificación asociada mediante GreenMail.
- [ ] Simular fallo SMTP y verificar el comportamiento transaccional definido, sin respuestas de éxito engañosas.
- [ ] Importación masiva: filas válidas/inválidas, duplicados, datos ficticios y habilitación del acceso.
- [ ] Migraciones: base nueva y actualización desde una versión anterior con datos representativos.

### Playwright smoke — P0

La suite debe usar frontend y backend reales contra PostgreSQL/GreenMail de prueba. Los mocks de navegador sirven para pruebas de UI, pero no sustituyen estos E2E.

| ID | Recorrido | Resultado esperado |
|---|---|---|
| E2E-01 | Login local de ADMIN, SOCIO y POSTULANTE | Cada rol llega a su módulo y no accede al ajeno. |
| E2E-02 | ADMIN filtra “Con deuda” e “Inactivo” | Resultados coherentes con saldo/estado, incluido inactivo con deuda. |
| E2E-03 | ADMIN registra cobro parcial y total de inactivo | Saldo persistido; botón desaparece al saldar; cuenta sigue inactiva. |
| E2E-04 | SOCIO publica oferta con nombre visible/reservado | POSTULANTE ve el nombre o “Empresa reservada”, según la opción guardada. |
| E2E-05 | POSTULANTE se postula con perfil y CV | Figura en Mis postulaciones y en postulaciones recibidas del socio; no se duplica. |
| E2E-06 | SOCIO comunica selección y no selección | Campana y correo capturado identifican empresa/oferta correctas. |
| E2E-07 | Usuario recibe comunicado con pop-up obligatorio | Bloquea según la regla acordada y no reaparece tras completarlo. |
| E2E-08 | POSTULANTE pausa/reanuda búsqueda | Persiste tras recargar; cambia la visibilidad para SOCIO, no para ADMIN. |

- [ ] Implementar estos recorridos y ejecutarlos en GitHub Actions con fixtures independientes.
- [ ] Guardar reportes, trazas y capturas ante fallos, sin tokens ni datos personales reales.
- [ ] Evitar esperas arbitrarias y reintentos que oculten fallas; usar condiciones observables.

**Salida de fase:** integración y smoke verdes en CI, desde una base limpia, sin RDS de producción ni correos reales.

## 6. Manual/UAT — P0/P1

- [ ] Google OAuth real: login, registro, vínculo, cancelación y errores; usar cuentas de prueba autorizadas.
- [ ] Gmail real: comprobar recepción, remitente, contenido, botones y carpeta de spam con destinatarios de prueba.
- [ ] Abrir una muestra de Excel en una aplicación de planillas para compatibilidad visual; verificar automáticamente filas, fechas, importes y totales en la suite.
- [ ] Revisar aceptación visual en escritorio y móvil; automatizar previamente formularios, campana, modales y estados vacío/cargando/error.
- [ ] Comprobar activación y zona horaria del scheduler en entorno autorizado; los límites, efectos e idempotencia de aniversarios/retención se verifican automáticamente con tiempo controlado.
- [ ] Realizar UAT por rol con representantes del equipo/CCISJ y registrar aceptación o incidencias.

Las pruebas manuales con proveedores reales complementan GreenMail; no se ejecutan como envíos masivos desde CI. Cualquier comprobación en producción requiere autorización y no debe alterar registros reales del padrón.

## 7. Fase 3 — Profundización

- [ ] **P1:** límites de retención (dos meses, gracia, confirmación y ocultamiento), zona horaria Uruguay y cambios de fecha.
- [ ] **P1:** idempotencia de aniversarios, avisos y reingresos; recuperación tras caída y reejecución.
- [ ] **P1:** carreras entre cobros, reemplazos de CV, baja/reactivación y postulaciones duplicadas.
- [ ] **P1 / RNF-07:** medir filtrado de Caja, consultas contables y carga del catálogo; el requisito fija **< 2000 ms**, no un umbral libre a definir.
- [ ] **P1 / RNF-07:** fijar dataset, hardware/red, concurrencia, caché y cantidad de repeticiones antes de medir; separar latencia API y tiempo hasta UI utilizable. En filtro local de Caja medir desde la entrada/clic hasta la actualización renderizada.
- [ ] **P1 / RNF-07:** registrar muestras, máximo, mediana y p95, incluidos arranque frío/caliente. Cualquier muestra >= 2000 ms incumple el texto estricto bajo el escenario definido; un promedio o p95 favorable no demuestra por sí solo cumplimiento universal.
- [ ] **P2:** performance adicional de importación/exportación con volumen sintético representativo, sin atribuirles el umbral RNF-07 si no están en su alcance.
- [ ] **P2:** comprobar ausencia de degradación evidente, errores no controlados y crecimiento ilimitado de elementos UI.

## 8. Ejecución actual

Desde la raíz del backend:

```powershell
mvn --batch-mode clean verify
```

Las suites de base de datos tienen activación explícita:

| Suite | Propiedad JVM |
|---|---|
| ProfilePhotoDatabaseTest | `photoTestJdbcUrl` |
| PostulanteDisponibilidadDatabaseTest | `rf23TestJdbcUrl` |
| SingleCvDatabaseTest | `singleCvTestJdbcUrl` |
| SocioSubscriptionDatabaseTest | `subscriptionTestJdbcUrl` |
| CajaInactivePaymentsDatabaseTest | `cajaTestJdbcUrl` |
| OfertaCompanyVisibilityDatabaseTest | `offerVisibilityTestJdbcUrl` |

Antes de ejecutarlas, revisar en cada fixture la base/usuario de prueba que espera. No basta con pasar una URL ni deben apuntar a producción. La unificación de esa configuración y su provisión automática forma parte de Fase 2.

Ejemplo de estructura, reemplazando el marcador por una URL exclusivamente de pruebas:

```powershell
mvn "-Dtest=OfertaCompanyVisibilityDatabaseTest" "-DofferVisibilityTestJdbcUrl=<URL_JDBC_DE_PRUEBA>" test
```

Los comandos Playwright/GreenMail se documentarán al implementar su configuración; todavía no son comandos disponibles garantizados.

## 9. Evidencia y criterio de cierre

Para cada ejecución registrar: caso/RF, fecha, responsable, commits de ambos repositorios, entorno, datos sintéticos, pasos, resultado esperado/obtenido, evidencia e incidencia asociada.

| Caso/RF | Prioridad | Responsable | Resultado | Evidencia/incidencia |
|---|---|---|---|---|
| Por completar | P0/P1/P2 | Por asignar | Pendiente/Aprobado/Fallido/Bloqueado | Enlace a reporte, captura o issue |

La etapa se considera completa cuando:

- [ ] Todos los P0 pasan y no hay defectos críticos ni altos abiertos.
- [ ] Los RF tienen trazabilidad a pruebas y evidencia.
- [ ] Cada RF/RNF parcial o no encontrado tiene implementación completada o una modificación de alcance aprobada. No declarar cumplimiento total mientras haya brechas sin resolver.
- [ ] RNF-07 tiene mediciones contra < 2 s y RNF-03/RNF-04 tienen evidencia externa cuando corresponda; ausencia de evidencia no equivale a aprobación.
- [ ] Las suites unitarias, de integración y smoke corren repetiblemente en CI; las omisiones están justificadas.
- [ ] La entrega real por Gmail y Google OAuth tienen evidencia manual.
- [ ] Se revisaron Excel, jobs y flujos de los tres roles.
- [ ] Los defectos restantes están priorizados, con responsable y aceptación explícita del riesgo.
- [ ] Se ejecutó la regresión final sobre los commits candidatos a entrega y se archivó el informe de resultados/UAT.

Este plan no modifica funcionalidades ni declara aprobadas las tareas pendientes.
