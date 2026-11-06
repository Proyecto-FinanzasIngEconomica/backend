# Despliegue de CrediFlow

Guía para quien despliegue el backend: **Render** (API en Docker) y **Aiven** (MySQL). También cubre cómo levantar todo en local con Docker Compose.

> Resumen: Aiven aloja la base de datos MySQL; Render construye el `Dockerfile` del repositorio y corre la API; las claves y la configuración van en **variables de entorno**, nunca en el repo.

---

## 1. Requisitos

- Acceso de lectura al repositorio de GitHub (Render lo conecta con GitHub).
- Una cuenta en [Aiven](https://aiven.io) y otra en [Render](https://render.com).
- Un canal privado del grupo para compartir las credenciales (nunca el repositorio, nunca el chat público).

---

## 2. Base de datos en Aiven (MySQL)

1. Crea un servicio **MySQL** con el plan gratuito.
2. Espera a que esté en estado *Running*. En la pestaña **Overview** anota: **Host**, **Port**, **User** (normalmente `avnadmin`), **Password** y **Database name** (normalmente `defaultdb`).
3. Revisa **Allowed IP addresses**: debe permitir conexiones desde fuera (por defecto abre todo). Render no tiene una IP fija, así que no se puede limitar a una sola.
4. Con eso se arma la URL de conexión (Aiven **exige SSL**):

   ```
   jdbc:mysql://<HOST>:<PORT>/<DATABASE>?sslMode=REQUIRED
   ```

   El puerto **no es 3306**: usa el que muestra Aiven.

> Comprueba en la consola de Aiven los límites del plan gratuito (almacenamiento, conexiones y qué pasa si el servicio está inactivo). Si el servicio se apaga por inactividad, hay que encenderlo desde la consola antes de una demostración.

Las tablas las crea la aplicación sola al primer arranque (ver sección 7: mejoras pendientes).

---

## 3. API en Render

1. En Render: **New → Web Service → Build and deploy from a Git repository** y elige el repositorio.
2. **Language / Runtime:** `Docker` (Render detecta el `Dockerfile` de la raíz).
3. **Instance type:** `Free`.
4. **Health Check Path:** `/actuator/health`.
5. En **Environment** agrega las variables de la sección 4.
6. **Create Web Service.** La primera construcción tarda varios minutos (compila con Maven dentro de Docker).

Render define `PORT` por su cuenta; la aplicación ya lo lee. Cada `push` a la rama conectada vuelve a desplegar.

---

## 4. Variables de entorno

| Variable | Obligatoria | Ejemplo / descripción |
|---|---|---|
| `DB_URL` | Sí | `jdbc:mysql://<HOST>:<PORT>/defaultdb?sslMode=REQUIRED` |
| `DB_USERNAME` | Sí | `avnadmin` |
| `DB_PASSWORD` | Sí | La contraseña de Aiven |
| `CREDIFLOW_JWT_SECRET` | Sí | Cadena aleatoria de **32 caracteres o más**. Si falta o es corta, la API no arranca. Generar una: `openssl rand -base64 48` |
| `CREDIFLOW_ADMIN_USERNAME` | Sí (1.er arranque) | Usuario del primer administrador del sistema (4–50 letras o números) |
| `CREDIFLOW_ADMIN_PASSWORD` | Sí (1.er arranque) | Contraseña del primer administrador (mínimo 8 caracteres) |
| `CORS_ALLOWED_ORIGINS` | Sí | URL(s) del frontend, separadas por coma. Ej.: `https://crediflow.onrender.com` |
| `SWAGGER_ENABLED` | No | `true` para mostrar Swagger UI (por defecto, apagado) |
| `STATEMENTS_SCHEDULER_ENABLED` | No | `false` por defecto. Ver sección 6 antes de activarlo |
| `DB_POOL_SIZE` | No | Conexiones máximas a la base. Por defecto 5 |

El perfil `prod` viene fijado en la imagen de Docker, así que **no hace falta** definir `SPRING_PROFILES_ACTIVE`. Esto es deliberado: si alguien olvidara la variable, la API no arrancaría con las credenciales de desarrollo (`admin` / `Admin12345`).

El administrador inicial solo se crea si **no existe ningún administrador del sistema**. Tras el primer ingreso, cámbiale la contraseña con `PATCH /api/v1/auth/password`.

---

## 5. Verificación después de desplegar

1. `GET https://<tu-servicio>.onrender.com/actuator/health` → `{"status":"UP"}`.
2. En los logs de Render debe verse: `Administrador del sistema inicial creado: <usuario>`.
3. `POST /api/v1/auth/login` con el administrador creado → **200** con un `accessToken`.
4. Si habilitaste `SWAGGER_ENABLED=true`, abre `/swagger-ui/index.html`.
5. Crea una tienda con `POST /api/v1/stores` (incluye el bloque `admin`), y sigue el recorrido de [flujos-de-la-aplicacion.md](flujos-de-la-aplicacion.md).

**Si algo falla**, el log de Render lo dice:

| Síntoma en el log | Causa probable |
|---|---|
| `Falta la clave del JWT` | `CREDIFLOW_JWT_SECRET` no definida o con menos de 32 caracteres |
| `Could not resolve placeholder 'CORS_ALLOWED_ORIGINS'` (o `DB_URL`…) | Falta esa variable de entorno |
| `Communications link failure` / `Connection refused` | Host o puerto de Aiven incorrectos, o el servicio de Aiven está apagado |
| `Access denied for user` | Usuario o contraseña de la base incorrectos |
| `SSL` / `Public Key Retrieval` | La URL no lleva `?sslMode=REQUIRED` |
| El servicio se reinicia en bucle sin error claro | Falta de memoria (plan gratuito de 512 MB); revisa que no se hayan cambiado las opciones de JVM del `Dockerfile` |

---

## 6. ESTADOS DE CUENTA EN PRODUCCIÓN (recordatorio importante)

El enunciado pide que el estado de cuenta se genere **automáticamente al llegar la fecha de corte**. La aplicación lo soporta con una tarea programada (`StatementScheduler`), pero **está apagada por defecto** (`STATEMENTS_SCHEDULER_ENABLED=false`). Esto es lo que hay que recordar:

1. **Con la tarea apagada, los estados se generan a mano**: `POST /api/v1/stores/{storeId}/clients/{clientId}/statements`. Con el cuerpo `{ "asOf": "2026-10-21T09:00:00" }` se simula otra fecha; sin cuerpo, usa la hora actual. **Para la exposición, esto es lo más seguro.**
2. **Si se enciende la tarea**, cada hora revisa a todos los clientes con condición vigente y emite lo que corresponda con la **hora real**. Es idempotente (no duplica un estado del mismo ciclo).
3. **No la enciendas mientras haya datos de prueba con fechas del pasado** (por ejemplo compras de septiembre). La tarea emitiría el estado del ciclo actual y después ya no se podrían generar a mano los ciclos anteriores (*"ya existe un estado de un ciclo posterior"*). Enciéndela solo en un despliegue limpio o en uso real.
4. **El plan gratuito de Render duerme el servicio tras ~15 minutos sin tráfico**, y mientras duerme la tarea no corre. Entonces el estado de cuenta se emitiría con retraso (al despertar, cuando llegue la próxima petición). Opciones:
   - Mantenerla apagada y generar a mano en la demo (recomendado para el curso).
   - Mantener el servicio despierto con un monitor externo gratuito (por ejemplo UptimeRobot) que consulte `/actuator/health` cada 5 minutos. El plan gratuito da ~750 horas al mes, suficientes para **un** servicio encendido todo el mes.
   - Pasar a un plan de pago.
5. **Zona horaria:** el corte se evalúa en hora de Lima (`America/Lima`). La imagen de Docker ya la fija; si alguna vez se despliega sin Docker, hay que arrancar la JVM con `-Duser.timezone=America/Lima`. Si no, un corte "día 20 a las 18:00" se evaluaría desplazado 5 horas.
6. **Una compra con fecha retroactiva en un ciclo que ya tiene estado de cuenta emitido se rechaza (422)**: es una regla, no un fallo.

Para activar la generación automática: `STATEMENTS_SCHEDULER_ENABLED=true` (y opcionalmente, con propiedades de la aplicación, `crediflow.statements.scheduler-interval-ms` y `crediflow.statements.scheduler-initial-delay-ms`).

---

## 7. Limitaciones conocidas y mejoras pendientes

- **Esquema de base de datos con `ddl-auto=update`.** Hibernate crea y ajusta las tablas al arrancar. Sirve para el primer despliegue con base vacía, pero no es lo ideal para producción. **Pendiente:** migraciones con **Flyway**, cambiar a `ddl-auto=validate` y añadir las restricciones únicas en la propia base (estado de cuenta único por cliente y ciclo, número de cuota único por compra, documento único por tienda, una sola condición de crédito activa por cliente).
- **La contraseña local `123456789` estuvo escrita en `application.properties`** y quedó en el historial de Git. Si el repositorio es público, **cambia esa contraseña** en tu MySQL local (aunque ya no aparezca en el archivo actual).
- **Plan gratuito de Render:** el primer request tras dormir tarda cerca de un minuto. Avísale al equipo de frontend para que su aplicación muestre un estado de carga en la primera llamada.
- **Un token no se puede cerrar antes de tiempo**, pero dar de baja o bloquear una cuenta sí corta el acceso al instante (la API revisa la cuenta en cada petición).

---

## 8. Ejecución local con Docker Compose

Para que cualquiera (incluido el equipo de frontend) levante todo con un comando, sin instalar MySQL:

```bash
docker compose up --build
```

- API: `http://localhost:8080` (Swagger en `http://localhost:8080/swagger-ui/index.html`).
- MySQL: `localhost:3307` (usuario `root`, contraseña `crediflow_local`, base `store_credit_management`).
- Perfil `dev`: administrador `admin` / `Admin12345`. **Solo para desarrollo.**

Para detener: `docker compose down` (conserva los datos); `docker compose down -v` (borra también la base).

### Sin Docker (IDE)

1. Crea un archivo `.env.properties` en la raíz del proyecto (está en `.gitignore`):

   ```properties
   spring.datasource.password=TU_CONTRASEÑA_DE_MYSQL
   ```

   Hay un `.env.properties.example` como plantilla.
2. Ejecuta con el perfil por defecto (`dev`).
