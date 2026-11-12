# SSDLC — Bolsa de Trabajo CCISJ

Planificación, análisis y diseño de seguridad (básico). Las herramientas SAST/SCA/DAST **no van al CI**: se corren en local (`scripts/seguridad` en la carpeta del proyecto).

## 1. Activos

| Activo | Dónde | Impacto si se filtra o altera |
| --- | --- | --- |
| Credenciales y JWT | `usuarios`, sessionStorage | Suplantación de admin, socio o postulante |
| Cédulas, BPS, RUT | postulantes / socios | Datos personales (Ley 18.331) |
| CV y fotos | S3 + base | Currículums de terceros |
| Caja (saldos, cobros) | cuotas, pagos, reintegros | Fraude contable |
| Claves AWS / JWT secret | runtime.env, GitHub Secrets | Acceso a infra y tokens |

## 2. Amenazas y riesgos

| Amenaza | Riesgo | Control |
| --- | --- | --- |
| Fuerza bruta de login | Alto | Límite de intentos (5 / 15 min) + BCrypt |
| Enumeración de correo/cédula | Medio | `/auth/register/availability` responde siempre genérico |
| Clave provisional adivinable | Alto | Clave aleatoria de 14 caracteres (no usa razón social) |
| JWT vivo tras logout | Alto | `token_version` + logout en servidor; JWT 2 h |
| Socio ve todos los CV de un perfil | Alto | Solo el CV de la postulación a su oferta |
| Upload con Content-Type falso | Alto | Firma real (PDF/DOCX, JPG/PNG/WEBP); PDF parseado con tope de páginas/archivo temp |
| Escalada de rol | Alto | `SecurityConfig`: `/admin/**`, `/socio/**`, `/postulante/**`, `/ofertas` admin/socio |
| Clickjacking / MIME sniffing | Medio | Cabeceras Nginx + filtro API |
| Dependencias viejas | Medio | SCA local (Trivy). Spring Boot 3.3.3: upgrade pendiente |
| SQL injection | Bajo | Consultas parametrizadas (ya estaba) |
| XSS | Bajo | Vue escapa HTML; no hay `v-html` |

## 3. Desarrollo (controles)

- Autenticación: BCrypt, política de clave, provisional 24 h, Google con token distinto al de sesión.
- Autorización declarada por rol. Usuario inactivo no entra. Importado masivo sin `acceso_habilitado` no entra.
- Validación de entradas (Bean Validation + firmas de archivo).
- Cifrado en tránsito (HTTPS en prod) y secretos fuera del repo (`.gitignore`).
- Logout invalida el JWT en servidor. Cambio de clave también.

## 4. Testing y despliegue (sin CI nuevo)

Correr en la máquina, **no** en GitHub Actions. Desde `Proyecto Final`:

```powershell
.\scripts\seguridad\sast.ps1
.\scripts\seguridad\sca.ps1
.\scripts\seguridad\dast.ps1
```

Anotar hallazgos en `scripts/seguridad/HALLAZGOS.md`.

**Hardening prod (pendiente de deploy):** cabeceras en `nginx.conf` del frontend. Rol IAM en la EC2: no se tocó el job de CI.

**SCA producto:** Spring Boot 3.3.3 ya no recibe parches públicos OSS. Subir a 3.5.x cuando se dé el OK.

## 5. Términos y condiciones

Ruta pública `/terminos`. El alta de postulante exige checkbox `acceptedTerms`.
