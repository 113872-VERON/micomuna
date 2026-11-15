# MiComuna

Gestor de reclamos municipales para pueblos y comunas. El vecino reporta un problema con foto y ubicación en el mapa, el personal municipal lo asigna a un agente y el reclamo avanza por los estados recibido, asignado, en reparación y resuelto. Una IA sugiere la categoría, detecta duplicados y propone la prioridad; solo sugiere, la decisión es siempre de una persona.

Proyecto final de la Tecnicatura Universitaria en Programación (UTN FRC Córdoba). Seguimiento en Jira: <https://113872-micomuna.atlassian.net/browse/MC>.

## Tecnologías

| Parte | Tecnología |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Maven |
| Base de datos | Oracle Database Free en Docker, migraciones con Flyway |
| Frontend | Angular 22 y Angular Material |

## Estructura del repositorio

```
backend/    API en Spring Boot (paquete com.micomuna)
frontend/   Aplicación Angular
db/         docker-compose.yml de Oracle y variables de ejemplo
docs/       análisis funcional (docs/analisis) y contrato de la API (docs/api)
```

## Levantar el entorno desde cero

### Requisitos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (con Docker Compose).
- Java 21 (JDK).
- Node.js 24 con npm.
- Git.

Maven no hace falta instalarlo: el backend trae su propio wrapper (`mvnw`).

### 1. Clonar el repositorio

```bash
git clone https://github.com/113872-VERON/micomuna.git
cd micomuna
```

### 2. Base de datos (Oracle Free en Docker)

Copiar el archivo de variables y cambiar las claves:

```bash
cd db
cp .env.example .env
```

En Windows con PowerShell, el equivalente es `Copy-Item .env.example .env`.

Contenido de `.env`:

| Variable | Qué es |
| --- | --- |
| `ORACLE_PASSWORD` | Clave del administrador de Oracle (`SYS`/`SYSTEM`) |
| `APP_USER` | Usuario que usa la aplicación (por defecto `micomuna`) |
| `APP_USER_PASSWORD` | Clave del usuario de la aplicación |
| `ORACLE_PORT` | Puerto local (por defecto `1522`, para no chocar con un Oracle instalado en Windows que usa el 1521) |
| `JWT_SECRET` | Secreto para firmar los tokens de sesión. Al menos 32 caracteres, aleatorio y distinto en cada entorno. El backend no arranca sin él |
| `SUPERADMIN_CORREO` y `SUPERADMIN_CLAVE` | Correo y clave (de 8 a 72 caracteres) del superadmin de la plataforma. Opcionales: ver "Primer superadmin" |

El archivo `.env` no se sube a git. Levantar la base:

```bash
docker compose up -d
```

La primera vez descarga la imagen y tarda unos minutos en iniciar. Para saber si está lista, el estado tiene que figurar como `healthy`:

```bash
docker compose ps
```

La base se llama `FREEPDB1`. Los datos quedan en un volumen de Docker, así que sobreviven a reinicios. Para apagarla sin perder datos: `docker compose down`. Para borrar también los datos y empezar de cero: `docker compose down -v`.

**Correo de desarrollo (Mailpit).** El mismo `docker compose up -d` levanta Mailpit, una casilla falsa que recibe los correos de la app (invitaciones a administradores y recuperación de contraseña) sin mandarlos a nadie. Se ven en <http://localhost:8025>. Los enlaces de esos correos apuntan a `http://localhost:4200`; si el frontend corre en otra dirección, definir `FRONTEND_URL`. Para un SMTP real se usan `MAIL_HOST`, `MAIL_PORT`, `MAIL_USUARIO`, `MAIL_CLAVE` y `MAIL_REMITENTE` (opcionales en desarrollo).

### 3. Backend

```bash
cd backend
./mvnw spring-boot:run
```

En Windows con PowerShell: `.\mvnw spring-boot:run`.

- Corre en <http://localhost:8081> (con el perfil `dev`). La comprobación `http://localhost:8081/api/salud` responde `{"estado":"ok"}` sin iniciar sesión.
- Usa el perfil `dev`, que está activo por defecto. Lee la conexión y las claves de `db/.env`, por eso hay que haber hecho el paso 2 antes.
- Al iniciar, Flyway aplica las migraciones de `backend/src/main/resources/db/migration`. En una base vacía se crean las tablas solas.
- Para correr las pruebas (necesitan la base levantada): `./mvnw test`.
- **Swagger** (documentación de la API, solo con el perfil `dev`): <http://localhost:8081/swagger-ui.html>. Los endpoints protegidos se prueban con el botón "Authorize" y un token que devuelve el login.
- La duración del token sale de `jwt.expiracion` (por defecto `4h`; se cambia con la variable `JWT_EXPIRACION`).

#### Primer superadmin

El superadmin es el único usuario que no pertenece a una comuna: crea las comunas e invita a su administrador. Se crea solo al iniciar el backend, si todavía no existe ninguno y están definidos `SUPERADMIN_CORREO` y `SUPERADMIN_CLAVE` en `db/.env`. Cambiar esas variables después no modifica al que ya existe. Ingresa en <http://localhost:4200/admin/login>.

#### Cómo se entra a cada comuna

Cada comuna tiene su propio enlace, armado con el nombre y la provincia (por ejemplo `salsipuedes-cordoba`). Los vecinos se registran e ingresan desde `/<enlace>/login`, y las pantallas se ven con el nombre, el logo y el color de esa comuna. Las comunas y sus administradores los da de alta el superadmin; al crear una comuna, su administrador recibe una invitación por correo para elegir su contraseña (nadie ve ni comparte una clave).

### 4. Frontend

```bash
cd frontend
npm install
npm start
```

Se abre en <http://localhost:4200>. La raíz no es una pantalla: se entra por el enlace de una comuna (`/<enlace>/login`) o por `/admin/login` si sos superadmin. Las llamadas a `/api` se reenvían al backend (puerto 8081) con `frontend/proxy.conf.json`; si se cambia ese archivo hay que reiniciar `npm start`. Pruebas: `npm test`. Compilación de producción: `npm run build`.

## Problemas comunes

- **El backend dice que no puede conectarse:** revisar que el contenedor esté `healthy` (`docker compose ps` dentro de `db/`) y que exista `db/.env`.
- **"ports are not available" al levantar Docker:** el puerto está ocupado por otro programa. Cambiar `ORACLE_PORT` en `db/.env`.
- **"Port 8081 was already in use" al iniciar el backend:** otro programa usa ese puerto. Cambiarlo con `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=<otro>` y actualizar `target` en `frontend/proxy.conf.json`.
- **Falta la clave (`APP_USER_PASSWORD`) al iniciar el backend:** el backend se ejecutó desde otra carpeta o falta `db/.env`. Ejecutarlo desde `backend/`.

## Cómo trabajamos

- Una rama por tarea de Jira: `feature/MC-<número>-descripcion-corta`.
- Los mensajes de commit empiezan con la clave de Jira (por ejemplo `MC-39 Documentacion del entorno`).
- Cada cambio entra a `main` por pull request. GitHub Actions compila y prueba el backend y el frontend en cada PR.
