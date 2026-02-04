# WSTun Architecture

## Overview

WSTun is a WebSocket-based relay server that enables real-time communication between services and users without requiring direct connections. The server acts as a central hub that routes messages between different types of clients.

## Core Concepts

### 1. Server

The WSTun server is an HTTP/WebSocket server that:
- Accepts WebSocket connections from service clients and user clients
- Routes messages between connected clients
- Serves static content (HTML, JS) for built-in services
- Provides REST API for management operations

### 2. Services

A **Service** is a type of application (e.g., `fileshare`, `chat`) that can be hosted on the WSTun server. Services are defined by:
- A unique name (e.g., `fileshare`)
- HTML/JS files that implement the service logic
- Endpoints (e.g., `/service`, `/main`)

Services can be:
- **Built-in**: Pre-installed with the server (fileshare, chat)
- **Marketplace**: Installed from external marketplace URLs

### 3. Service Instances (Rooms)

A **Service Instance** is a specific running session of a service. For example, a `fileshare` service can have multiple file-sharing rooms, each being an instance.

Each instance has:
- **UUID**: Unique identifier (e.g., `a1b2c3d4`)
- **Name**: Human-readable name (e.g., "Team Files")
- **Token** (optional): Password for joining
- **Owner**: The service client that created the instance

### 4. Client Types

#### Service Client (Instance Host)
- Creates and manages service instances
- Receives notifications when users join/leave
- Can kick users from the instance
- Coordinates data between user clients

#### User Client (Instance Member)
- Joins an existing service instance
- Sends/receives data through the instance
- Can be kicked by the instance host

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        WSTun Server                             │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    HTTP Handler                            │  │
│  │  - Serves static files (HTML, JS)                         │  │
│  │  - REST API (/_api/...)                                   │  │
│  │  - WebSocket upgrade                                       │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                 WebSocket Handler                          │  │
│  │  - Message routing                                        │  │
│  │  - Instance management                                    │  │
│  │  - Client registration                                    │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                 Service Manager                            │  │
│  │  - Tracks registered services                             │  │
│  │  - Manages service instances                              │  │
│  │  - Handles client connections                             │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
        │                    │                    │
        │ WebSocket          │ WebSocket          │ WebSocket
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ Service Client│    │  User Client  │    │  User Client  │
│ (Instance Host)    │  (Member)     │    │  (Member)     │
│               │    │               │    │               │
│ /fileshare/   │    │ /fileshare/   │    │ /fileshare/   │
│ service       │    │ main          │    │ main          │
└───────────────┘    └───────────────┘    └───────────────┘
```

## Message Flow

### 1. Instance Creation

```
Service Client                  Server
     │                           │
     │──── create_instance ─────>│  (name, token, server_token)
     │                           │
     │<─── instance_created ─────│  (uuid, name)
     │                           │
```

### 2. User Joining

```
User Client                     Server                    Service Client
     │                           │                              │
     │──── join_instance ───────>│  (uuid, userId, token)       │
     │                           │                              │
     │                           │──── client_connected ───────>│
     │                           │                              │
     │<──────── ack ─────────────│  (success, instanceName)     │
     │                           │                              │
```

### 3. Data Exchange

All data exchange happens through the server. The service client acts as a coordinator:

```
User Client A                   Server                    User Client B
     │                           │                              │
     │──── file_register ───────>│                              │
     │                           │───(to service client)───────>│
     │                           │<──── file_list ──────────────│
     │<──── file_list ───────────│───(broadcast to all)────────>│
     │                           │                              │
```

## Authentication

### Server Authentication
- Optional server-wide token
- Required for all requests when enabled (except `/libwstun.js`)
- Passed via `Authorization: Bearer <token>` header or `?token=<token>` query param

### Instance Authentication
- Optional per-instance token
- Set when creating an instance
- Required for users to join that instance

## API Endpoints

### HTTP Endpoints

| Endpoint | Description |
|----------|-------------|
| `/` | Server info page |
| `/admin` | Service management UI |
| `/libwstun.js` | Client library |
| `/{service}/service` | Service controller page |
| `/{service}/main` | User client page |
| `/_api/services` | List installed services |
| `/_api/instances` | List running instances |
| `/_api/marketplace/*` | Marketplace operations |

### WebSocket Messages

| Message Type | Direction | Description |
|--------------|-----------|-------------|
| `create_instance` | Client → Server | Create a new instance |
| `instance_created` | Server → Client | Instance creation result |
| `join_instance` | Client → Server | Join an existing instance |
| `list_instances` | Client → Server | List available instances |
| `instance_list` | Server → Client | List of instances |
| `kick_client` | Host → Server | Kick a user from instance |
| `client_connected` | Server → Host | User joined notification |
| `client_disconnected` | Server → Host | User left notification |

## Data Storage

- **Server never stores user data**: All files, messages, etc. remain on client devices
- **Service Manager**: Tracks active connections and instances in memory
- **Marketplace Service**: Stores installed service manifests and files locally

## Best Practices

1. **Use libwstun.js**: The client library handles all WebSocket communication and authentication
2. **Instance-scoped data**: Keep data scoped to instances, not globally
3. **Handle disconnections**: Clean up resources when users disconnect
4. **Broadcast updates**: Notify all instance members when data changes
