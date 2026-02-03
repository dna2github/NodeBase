# WSTun - WebSocket Tunnel Server

An Android app that runs an HTTP/WebSocket server for service sharing in local networks.

## Features

### Server (Android App)
- HTTP server with WebSocket support using Netty
- Optional HTTPS with self-signed certificate
- Service registration via WebSocket
- HTTP request relay to connected services
- Foreground service for background operation
- Service management UI (view, kick)

### Services
- Connect via WebSocket and register endpoints
- Define HTTP routes that relay to the service
- Provide static resources (HTML/JS/CSS)
- No server-side storage - everything relayed

## Architecture

```
┌─────────────┐      WebSocket      ┌─────────────────┐
│   Service   │◄───────────────────►│    WSTun App    │
│   Client    │                     │  (HTTP Server)  │
└─────────────┘                     └────────┬────────┘
                                             │ HTTP
                                             ▼
                                    ┌─────────────────┐
                                    │   Web Browser   │
                                    │     (User)      │
                                    └─────────────────┘
```

1. Service connects via WebSocket and registers
2. User accesses `http://server/[service]/main`
3. Server relays request to service via WebSocket
4. Service sends response via WebSocket
5. Server sends HTTP response to user

## Building

### Android App

```bash
cd wstun
./gradlew assembleDebug
```

### Service Clients

```bash
cd services/fileshare
npm install

cd services/chat
npm install
```

## Usage

### Start the Server

1. Install the APK on an Android device
2. Configure port and HTTPS option
3. Tap "Start Server"
4. Note the displayed IP address

### Connect Services

```bash
# File sharing
cd services/fileshare
node client.js ws://192.168.1.100:8080/ws

# Chat
cd services/chat
node client.js ws://192.168.1.100:8080/ws
```

### Access Services

- Server info: `http://192.168.1.100:8080/`
- FileShare: `http://192.168.1.100:8080/fileshare/main`
- Chat: `http://192.168.1.100:8080/chat/main`

## Protocol

### Service Registration

```json
{
  "type": "register",
  "id": "unique-id",
  "payload": {
    "name": "servicename",
    "type": "service-type",
    "description": "Service description",
    "endpoints": [
      { "path": "/main", "method": "GET", "relay": true }
    ],
    "static_resources": {
      "/main": "<html>...</html>"
    }
  }
}
```

### HTTP Request Relay

Server sends to service:
```json
{
  "type": "http_request",
  "payload": {
    "request_id": "req-123",
    "method": "GET",
    "path": "/servicename/main",
    "headers": { "Content-Type": "text/html" },
    "body": "..."
  }
}
```

Service responds:
```json
{
  "type": "http_response",
  "payload": {
    "request_id": "req-123",
    "status": 200,
    "headers": { "Content-Type": "text/html" },
    "body": "<html>...</html>"
  }
}
```

## Included Services

### FileShare

Share files without server storage:
- Virtual folder tree
- File upload/download via browser
- Relay mode for temporary sharing
- All data flows through the client

### Chat

Real-time chat with rich messages:
- Text, Card, and Poll message types
- Broadcast to all or private messages
- Abstract JSON format for custom rendering
- Self-contained HTML/JS/CSS

## Security Notes

- HTTPS uses self-signed certificate (browser warning expected)
- No authentication built-in (add as needed)
- For local network use only
- Services should validate inputs

## License

MIT
