# tcpmon-tls

Proxy estilo `tcpmon` para depuración moderna de tráfico HTTP/TLS.

`tcpmon-tls` es una herramienta Java para depurar integraciones locales y remotas sobre TCP, TLS y HTTP/HTTPS. Permite inspeccionar `request/response`, interceptar tráfico, editar requests HTTP, reenviar al target, recapturar por el listener local y operar múltiples rutas en un solo proceso.

## Highlights

- múltiples listeners y targets por proceso usando `routes[]`
- inspección de `request/response` HTTP desde una UI web local
- interceptación, edición estructurada y reenvío de requests
- replay al target y recapture por el listener local
- soporte para `TLS` y `mTLS` inbound/outbound
- configuración por `JSON` y empaquetado como `jar` ejecutable

## Casos de uso típicos

- depurar `HTTP local -> HTTPS remoto`
- inspeccionar requests y responses sin modificar el cliente
- reproducir errores de integración desde tráfico ya capturado
- probar conectividad `TLS/mTLS` hacia backends remotos
- correr varias rutas locales contra distintos destinos

Actualmente el flujo más útil y probado es:

- `HTTP local -> HTTPS remoto`
- `HTTP local -> HTTP remoto`
- `TLS local -> TLS remoto`
- recaptura de requests desde la UI local

## Qué hace

- expone un listener local TCP o TLS
- reenvía el tráfico a un destino TCP o TLS
- soporta `mTLS` inbound y outbound
- guarda sesiones y payloads en disco
- expone una UI web local para inspección
- separa `request` y `response` HTTP cuando puede detectarlos
- soporta múltiples intercambios HTTP en una misma sesión keep-alive
- permite reenviar un request:
  - al listener local para recapturarlo
  - directo al target configurado

## Requisitos

- Java 21
- Maven 3.9+ para compilar

## Build

```bash
mvn -q package -DskipTests
```

Jar resultante:

```text
target/tcpmon-tls-0.1.0-SNAPSHOT.jar
```

## Ejecución rápida

Genera un archivo JSON de ejemplo:

```bash
java -jar target/tcpmon-tls-0.1.0-SNAPSHOT.jar --init-config tcpmon.json
```

Arranca usando ese archivo:

```bash
java -jar target/tcpmon-tls-0.1.0-SNAPSHOT.jar --config tcpmon.json
```

## Caso recomendado: HTTP local -> HTTPS remoto

Ejemplo con `jsonplaceholder`:

```json
{
  "listener": {
    "host": "127.0.0.1",
    "port": 9000,
    "mode": "PLAIN"
  },
  "target": {
    "host": "jsonplaceholder.typicode.com",
    "port": 443,
    "mode": "TLS",
    "sni": "jsonplaceholder.typicode.com",
    "insecure": true,
    "rewriteHostHeader": true
  },
  "ui": {
    "host": "127.0.0.1",
    "port": 8080,
    "enabled": true
  },
  "sessionsDir": "./sessions",
  "interceptMode": "NONE",
  "tlsProtocols": ["TLSv1.3", "TLSv1.2"]
}
```

Arranque:

```bash
java -jar target/tcpmon-tls-0.1.0-SNAPSHOT.jar --config tcpmon.json
```

Prueba local:

```bash
curl -v http://127.0.0.1:9000/posts/1
```

UI local:

```text
http://127.0.0.1:8080/
```

## Ejecución por flags

El mismo ejemplo anterior sin archivo:

```bash
java -jar target/tcpmon-tls-0.1.0-SNAPSHOT.jar \
  --listen-host 127.0.0.1 \
  --listen-port 9000 \
  --listen-mode PLAIN \
  --target-host jsonplaceholder.typicode.com \
  --target-port 443 \
  --target-mode TLS \
  --target-sni=jsonplaceholder.typicode.com \
  --target-insecure=true \
  --rewrite-host-header=true \
  --ui-enabled=true \
  --ui-host 127.0.0.1 \
  --ui-port 8080
```

## Opciones más importantes

### Listener local

- `--listen-host`
- `--listen-port`
- `--listen-mode=PLAIN|TLS`
- `--listen-client-auth=NONE|OPTIONAL|REQUIRE`
- `--listen-cert`
- `--listen-key`
- `--listen-keystore`
- `--listen-truststore`

### Target remoto

- `--target-host`
- `--target-port`
- `--target-mode=PLAIN|TLS`
- `--target-sni`
- `--target-insecure`
- `--target-verify-hostname`
- `--rewrite-host-header`
- `--target-cert`
- `--target-key`
- `--target-keystore`
- `--target-truststore`

### UI y sesiones

- `--ui-enabled`
- `--ui-host`
- `--ui-port`
- `--sessions-dir`
- `--intercept-mode=NONE|REQUEST|RESPONSE|BOTH`

### Configuración por archivo

- `--config <ruta>`
- `--init-config <ruta>`

El archivo JSON soporta dos formas:

- modo simple: `listener` + `target`
- modo multi-route: `routes[]`

## Multi-route en un solo proceso

También puedes definir múltiples listeners, cada uno con su target propio, dentro del mismo proceso.

Ejemplo:

```json
{
  "routes": [
    {
      "id": "public-api",
      "listener": {
        "host": "127.0.0.1",
        "port": 9000,
        "mode": "PLAIN"
      },
      "target": {
        "host": "jsonplaceholder.typicode.com",
        "port": 443,
        "mode": "TLS",
        "sni": "jsonplaceholder.typicode.com",
        "insecure": true,
        "rewriteHostHeader": true
      }
    },
    {
      "id": "legacy-http",
      "listener": {
        "host": "127.0.0.1",
        "port": 9001,
        "mode": "PLAIN"
      },
      "target": {
        "host": "example.org",
        "port": 80,
        "mode": "PLAIN"
      }
    }
  ],
  "ui": {
    "host": "127.0.0.1",
    "port": 8080,
    "enabled": true
  },
  "sessionsDir": "./sessions",
  "interceptMode": "NONE"
}
```

Con este archivo:

- `127.0.0.1:9000` apunta al target TLS definido en `public-api`
- `127.0.0.1:9001` apunta al target plano definido en `legacy-http`
- cada sesión queda etiquetada por `routeId`

Arranque:

```bash
java -jar target/tcpmon-tls-0.1.0-SNAPSHOT.jar --config tcpmon.json
```

Notas:

- cuando usas `routes[]`, esa lista define completamente los listeners/targets del proceso
- los flags CLI actuales siguen siendo útiles para el modo de una sola ruta
- la UI muestra `routeId` por sesión para distinguir de qué listener provino el tráfico

## Booleanos en CLI

Las opciones booleanas aceptan ambos estilos:

```bash
--target-insecure
--target-insecure=true
```

Lo mismo aplica para:

- `--ui-enabled`
- `--target-verify-hostname`
- `--rewrite-host-header`

## Certificados y TLS

Se soporta carga de material TLS por:

- `PEM`
- `JKS`
- `PKCS12`

Uso típico:

- `cert + key PEM` para server/client cert
- `truststore PEM/JKS/P12` para validar peers

### Cuándo usar `--target-sni`

Sirve para indicar el hostname que se enviará en el handshake TLS hacia el remoto.

Es útil cuando:

- el socket se abre contra una IP pero el certificado está emitido para un hostname
- el servidor remoto usa virtual hosting TLS
- quieres desacoplar `target-host` del nombre anunciado en SNI

## `--target-insecure`

Deshabilita la validación del certificado remoto en TLS outbound.

Está pensado para:

- pruebas locales
- ambientes con certificados internos no confiables aún

No debería usarse como default en producción.

## `--rewrite-host-header`

Reescribe el header `Host` del request HTTP antes de enviarlo al target remoto.

Es útil en flujos como:

- `curl http://127.0.0.1:9000/...`
- target remoto HTTPS que espera `Host: api.example.com`

Sin esta opción, muchos backends devolverán `403`, `421` o respuestas incorrectas.

## UI local

La UI muestra:

- lista de sesiones
- `routeId` por sesión
- metadata TLS inbound/outbound
- lista de exchanges HTTP detectados
- request y response por exchange
- headers y body cuando el payload puede parsearse como HTTP
- eventos raw capturados por la sesión

### Acciones disponibles

Para payloads `CLIENT_TO_TARGET`:

- `Recapture request`
  - reenvía el request al listener local
  - el request entra otra vez por el proxy
  - se captura como una nueva sesión

- `Send direct`
  - reenvía el request directo al target actual

Para payloads interceptados:

- `Forward original`
- `Edit/Forward`

## Persistencia

Las sesiones se guardan en el directorio configurado en `sessionsDir`.

Estructura típica:

```text
sessions/
├── sessions.jsonl
└── blobs/
    └── <session-id>/
        └── <event-id>.bin
```

### Qué se guarda

- apertura y cierre de sesión
- errores y eventos de ciclo de vida
- metadata TLS
- payloads request/response
- referencias a blobs binarios

## Intercepción

`--intercept-mode` soporta:

- `NONE`
- `REQUEST`
- `RESPONSE`
- `BOTH`

Cuando una dirección está interceptada:

- el payload no se reenvía inmediatamente
- queda pendiente en memoria
- puedes reenviarlo original o editarlo desde la UI

## Limitaciones actuales

- el parser HTTP de la UI soporta `Content-Length`, `Transfer-Encoding: chunked`, `gzip`, `deflate` y `br`
- no interpreta todavía:
  - WebSocket
  - streaming HTTP incremental
- la recaptura local a listener `TLS` con `client auth REQUIRE` no presenta certificado cliente todavía
- la herramienta está optimizada para depuración local, no para throughput alto
- la UI está enfocada en HTTP; para tráfico TCP genérico cae a vista raw

## Desarrollo

Ejecutar pruebas:

```bash
mvn -q test
```

## Estructura del proyecto

```text
src/main/java/com/cafeina/tcpmon/
├── config/     # carga de config JSON
├── proxy/      # listener, bridge y reescritura HTTP
├── replay/     # reenvío a listener o target
├── session/    # modelo y persistencia de sesiones
├── tls/        # construcción de contextos TLS
├── util/       # helpers
└── web/        # API local y UI
```

## Estado actual

El proyecto ya tiene:

- build funcional
- pruebas unitarias e integración
- UI local utilizable
- flujo útil para depuración de APIs HTTP/HTTPS

Si vas a continuar el desarrollo, los siguientes pasos con más valor técnico serían:

- exportación/importación de exchanges
- filtros y búsqueda por `routeId`
