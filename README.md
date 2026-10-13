# RabbitMQ Cluster con Docker Compose + Spring Boot

Actividad de la guía **2.3.1 – RabbitMQ Cluster con Docker Compose** (Escuela de Informática y Telecomunicaciones, Duoc UC).

El proyecto levanta un clúster de RabbitMQ de 2 nodos con Docker Compose y una aplicación Spring Boot que:

- Gestiona colas, exchanges y bindings en tiempo de ejecución con `RabbitAdmin`.
- Consume mensajes de `orders.queue` con un listener (`order-listener`).
- Permite pausar y reanudar ese listener mediante una API REST.

## Tecnologías

| Componente | Versión |
|---|---|
| RabbitMQ | 3.12 (`rabbitmq:3.12-management-alpine`) |
| Spring Boot | 4.1.1 |
| Java | 17 o superior (probado con JDK 25) |
| Maven | Incluido vía Maven Wrapper (`mvnw`) |
| Docker / Docker Compose | Docker Desktop con Compose v2 |

## Estructura del proyecto

```
rabbitmq-avanzado/
├── docker-compose.yml          # Clúster de 2 nodos RabbitMQ
├── rabbitmq.conf               # Configuración de clustering (peer discovery)
├── pom.xml
└── src/main/java/com/example/
    ├── RabbitMQAdvancedApplication.java
    ├── config/
    │   ├── RabbitAdminConfig.java       # Beans RabbitAdmin y RabbitTemplate
    │   └── RabbitListenerConfig.java    # Factories de listeners + cola orders.queue
    ├── controller/
    │   ├── RabbitMQController.java      # /api/rabbitmq
    │   └── ListenerController.java      # /api/listeners
    ├── listener/
    │   └── OrderListener.java           # Consume orders.queue
    └── service/
        ├── RabbitResourceManager.java   # Crear/eliminar/purgar recursos
        └── ListenerManagementService.java
```

## Cómo ejecutarlo

### 1. Levantar el clúster de RabbitMQ

```bash
docker compose up -d
docker compose ps
```

Verificar que ambos nodos forman el clúster:

```bash
docker exec rabbitmq-1 rabbitmqctl cluster_status
```

En la salida deben aparecer `rabbit@rabbitmq-1` y `rabbit@rabbitmq-2` en **Disk Nodes** y en **Running Nodes**.

### 2. Ejecutar la aplicación Spring Boot

Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run
```

Linux / macOS:

```bash
./mvnw spring-boot:run
```

> Si `java` no está en el PATH, define antes `JAVA_HOME` apuntando a tu JDK, por ejemplo:
> `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\openjdk-25.0.1"`

Resultado esperado en los logs:

```
Started RabbitMQAdvancedApplication in X.XXX seconds
```

## Management UI

| Nodo | URL | Usuario | Contraseña |
|---|---|---|---|
| Nodo 1 | http://localhost:15672 | `admin` | `admin123` |
| Nodo 2 | http://localhost:15673 | `admin` | `admin123` |

Las credenciales se definen en `docker-compose.yml` (`RABBITMQ_DEFAULT_USER` / `RABBITMQ_DEFAULT_PASS`). Son credenciales de práctica para uso local, no para producción.

## Endpoints

### Gestión de recursos (`/api/rabbitmq`)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/rabbitmq/queues?queueName=X` | Crea una cola durable |
| POST | `/api/rabbitmq/exchanges?exchangeName=X` | Crea un exchange direct |
| POST | `/api/rabbitmq/bindings?queueName=X&exchangeName=Y&routingKey=Z` | Crea un binding |
| GET | `/api/rabbitmq/queues/{queueName}` | Información de la cola (mensajes, consumidores) |
| DELETE | `/api/rabbitmq/queues/{queueName}` | Elimina la cola |
| POST | `/api/rabbitmq/queues/{queueName}/purge` | Vacía la cola |

### Control de listeners (`/api/listeners`)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/listeners` | Lista los IDs de listeners |
| POST | `/api/listeners/{listenerId}/pause` | Pausa el listener |
| POST | `/api/listeners/{listenerId}/resume` | Reanuda el listener |

## Pruebas

```bash
# Crear cola, exchange y binding
curl -X POST "http://localhost:8080/api/rabbitmq/queues?queueName=orders.queue"
curl -X POST "http://localhost:8080/api/rabbitmq/exchanges?exchangeName=orders.exchange"
curl -X POST "http://localhost:8080/api/rabbitmq/bindings?queueName=orders.queue&exchangeName=orders.exchange&routingKey=orders"

# Ver y controlar listeners
curl http://localhost:8080/api/listeners
curl -X POST "http://localhost:8080/api/listeners/order-listener/pause"
curl -X POST "http://localhost:8080/api/listeners/order-listener/resume"

# Ver estado de la cola
curl http://localhost:8080/api/rabbitmq/queues/orders.queue
```

Para enviar un mensaje de prueba, en el Management UI ve a **Exchanges → orders.exchange → Publish message**, usa routing key `orders` y un payload como `ORD-001`. El log de la app mostrará `Procesando orden: ORD-001`.

Validaciones en el Management UI (ambos nodos):

- ✅ Las colas aparecen en ambos nodos
- ✅ Los exchanges están replicados
- ✅ Los bindings son correctos
- ✅ Los mensajes se distribuyen entre consumidores

## Diferencias con la guía original

El código de la guía tenía algunos errores que impedían ejecutarlo; se corrigieron así:

1. **`rabbitmq.conf`**: se eliminó `management.load_definitions` porque apunta a un `definitions.json` inexistente (el nodo no arrancaba), y `queue_master_location = all` (inválido) se reemplazó por `queue_master_locator = min-masters`.
2. **`RabbitAdminConfig`**: el import correcto es `org.springframework.amqp.rabbit.connection.ConnectionFactory`.
3. **`RabbitListenerConfig`**: `SimpleRabbitListenerContainerFactory` está en `org.springframework.amqp.rabbit.config`. Además se declara `orders.queue` como `@Bean` para que el listener tenga una cola al iniciar.
4. **`ListenerManagementService`**: se corrigieron los imports (`org.springframework.amqp...`). Spring AMQP no tiene `pause()`/`resume()`, así que se usan `stop()`/`start()`, y los IDs se obtienen con `registry.getListenerContainerIds()`.
5. **Spring Boot 4.1.1** en vez de 3.2.x (la guía permite "3.2.x o superior"; Spring Initializr ya solo ofrece 4.x). Por eso el starter web es `spring-boot-starter-webmvc`.
6. Se quitó `version: '3.9'` de `docker-compose.yml` porque está obsoleto en Compose v2.

## Detener todo

```bash
# Detener la app: Ctrl + C en la terminal de Spring Boot

# Detener el clúster (conserva los datos)
docker compose down

# Detener el clúster y borrar los datos (volúmenes)
docker compose down -v
```
