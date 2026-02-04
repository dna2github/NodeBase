# libwstun.js - WSTun Client Library

## Overview

`libwstun.js` is the official JavaScript client library for WSTun. It provides a simple API for creating service instances (rooms) and joining them as users.

## Loading the Library

```html
<script src="/libwstun.js"></script>
```

The library is served directly by the WSTun server at `/libwstun.js`.

## API Reference

### WSTun Object

The main `WSTun` object provides factory methods and utilities:

```javascript
WSTun.version          // Library version string
WSTun.generateId()     // Generate a unique ID
WSTun.buildWsUrl(token) // Build WebSocket URL with optional token
```

### Creating an Instance Host (Service Controller)

Use `WSTun.createInstanceHost()` to create and manage a service instance (room).

```javascript
const host = WSTun.createInstanceHost({
    service: 'fileshare',           // Service name
    name: 'My Room',                // Room display name
    serverToken: 'secret123',       // Optional: server auth token
    instanceToken: 'roompass',      // Optional: token users need to join
    
    // Callbacks
    onOpen: () => { },                      // WebSocket connected
    onInstanceCreated: (payload) => { },    // Instance created successfully
    onMessage: (msg) => { },                // Received a message
    onClose: () => { },                     // Connection closed
    onError: (err) => { }                   // Error occurred
});

host.connect();  // Start connection
```

#### Instance Host Methods

```javascript
host.connect()           // Connect to server and create instance
host.disconnect()        // Close connection
host.send(type, payload) // Send a message
host.broadcast(type, payload) // Broadcast to all users in instance
host.kickUser(userId)    // Kick a user from the instance
```

#### Instance Created Payload

```javascript
{
    success: true,
    uuid: 'a1b2c3d4',    // Instance UUID
    name: 'My Room'       // Instance name
}
```

### Creating an Instance Client (User)

Use `WSTun.createInstanceClient()` to join an existing instance.

```javascript
const client = WSTun.createInstanceClient({
    service: 'fileshare',           // Service name
    instanceUuid: 'a1b2c3d4',       // Instance UUID to join
    serverToken: 'secret123',       // Optional: server auth token
    instanceToken: 'roompass',      // Optional: instance access token
    userId: 'user123',              // Optional: user ID (auto-generated if not set)
    
    // Callbacks
    onOpen: () => { },              // WebSocket connected
    onJoined: (payload) => { },     // Successfully joined instance
    onMessage: (msg) => { },        // Received a message
    onClose: () => { },             // Connection closed
    onError: (err) => { },          // Error occurred
    onKicked: (payload) => { }      // Kicked from instance
});

client.connect();  // Start connection
```

#### Instance Client Methods

```javascript
client.connect()           // Connect to server and join instance
client.disconnect()        // Leave instance and close connection
client.send(type, payload) // Send a message to the instance
```

#### Joined Payload

```javascript
{
    success: true,
    instanceUuid: 'a1b2c3d4',
    instanceName: 'My Room'
}
```

### Listing Available Instances

Use `WSTun.listInstances()` to get a list of available instances for a service.

```javascript
try {
    const instances = await WSTun.listInstances('fileshare', serverToken);
    // instances is an array of instance objects
    instances.forEach(inst => {
        console.log(inst.uuid, inst.name, inst.userCount, inst.hasToken);
    });
} catch (err) {
    console.error('Failed to list instances:', err);
}
```

#### Instance Object

```javascript
{
    uuid: 'a1b2c3d4',      // Instance UUID
    name: 'My Room',       // Display name
    service: 'fileshare',  // Service name
    userCount: 3,          // Number of connected users
    hasToken: true,        // Whether token is required to join
    createdAt: 1699000000  // Creation timestamp
}
```

## Complete Example: File Sharing Service

### Service Controller (index.html)

```html
<!DOCTYPE html>
<html>
<head>
    <title>FileShare Controller</title>
</head>
<body>
    <h1>FileShare Room</h1>
    <div id="status">Not connected</div>
    <button onclick="createRoom()">Create Room</button>
    <div id="users"></div>

    <script src="/libwstun.js"></script>
    <script>
let host = null;
const files = new Map();
const users = new Map();

function createRoom() {
    host = WSTun.createInstanceHost({
        service: 'fileshare',
        name: 'My FileShare Room',
        instanceToken: 'secretpass',
        
        onInstanceCreated: (payload) => {
            document.getElementById('status').textContent = 
                'Room created: ' + payload.uuid;
        },
        
        onMessage: (msg) => {
            switch (msg.type) {
                case 'file_register':
                    files.set(msg.payload.fileId, msg.payload);
                    // Broadcast updated file list to all users
                    host.broadcast('file_list', { 
                        files: Array.from(files.values()) 
                    });
                    break;
                    
                case 'client_connected':
                    users.set(msg.payload.userId, { connectedAt: Date.now() });
                    updateUserList();
                    break;
                    
                case 'client_disconnected':
                    users.delete(msg.payload.userId);
                    // Remove files owned by this user
                    for (const [id, f] of files) {
                        if (f.ownerId === msg.payload.userId) {
                            files.delete(id);
                        }
                    }
                    updateUserList();
                    break;
            }
        },
        
        onError: (err) => {
            document.getElementById('status').textContent = 'Error: ' + err.message;
        }
    });
    
    host.connect();
}

function updateUserList() {
    document.getElementById('users').innerHTML = 
        Array.from(users.keys())
            .map(uid => `<div>${uid} <button onclick="host.kickUser('${uid}')">Kick</button></div>`)
            .join('');
}
    </script>
</body>
</html>
```

### User Client (main.html)

```html
<!DOCTYPE html>
<html>
<head>
    <title>FileShare</title>
</head>
<body>
    <h1>FileShare</h1>
    <div id="status">Connecting...</div>
    <input type="file" id="fileInput" onchange="shareFile(this.files[0])">
    <div id="files"></div>

    <script src="/libwstun.js"></script>
    <script>
// Get instance UUID from URL
const params = new URLSearchParams(location.search);
const instanceUuid = params.get('instance');
const instanceToken = params.get('token');

const myFiles = new Map();
let client = null;

if (instanceUuid) {
    const userId = WSTun.generateId();
    
    client = WSTun.createInstanceClient({
        service: 'fileshare',
        instanceUuid: instanceUuid,
        instanceToken: instanceToken,
        userId: userId,
        
        onJoined: (payload) => {
            document.getElementById('status').textContent = 
                'Connected to ' + payload.instanceName;
        },
        
        onMessage: (msg) => {
            if (msg.type === 'file_list') {
                renderFileList(msg.payload.files);
            } else if (msg.type === 'file_request') {
                streamFile(msg.payload);
            }
        },
        
        onKicked: () => {
            alert('You have been kicked!');
            location.reload();
        },
        
        onError: (err) => {
            document.getElementById('status').textContent = 'Error: ' + err.message;
        }
    });
    
    client.connect();
}

function shareFile(file) {
    if (!file || !client) return;
    
    const fileId = 'f' + WSTun.generateId();
    myFiles.set(fileId, { file, filename: file.name, size: file.size });
    
    client.send('file_register', {
        fileId: fileId,
        filename: file.name,
        size: file.size,
        mimeType: file.type,
        ownerId: client.userId
    });
}

function renderFileList(files) {
    document.getElementById('files').innerHTML = files.map(f => 
        `<div>${f.filename} (${f.size} bytes) - <a href="/fileshare/download/${f.id}">Download</a></div>`
    ).join('');
}

function streamFile(payload) {
    const info = myFiles.get(payload.fileId);
    if (!info) return;
    
    // Send file in chunks via WebSocket
    // (actual implementation omitted for brevity)
}
    </script>
</body>
</html>
```

## Message Types

### Standard Messages

| Type | Direction | Description |
|------|-----------|-------------|
| `create_instance` | Host → Server | Create instance |
| `instance_created` | Server → Host | Instance created |
| `join_instance` | Client → Server | Join instance |
| `list_instances` | Client → Server | List instances |
| `instance_list` | Server → Client | Instance list |
| `kick_client` | Host → Server | Kick user |
| `kick` | Server → Client | You were kicked |
| `ack` | Server → Client | Operation acknowledged |
| `error` | Server → Client | Error occurred |
| `client_connected` | Server → Host | User joined |
| `client_disconnected` | Server → Host | User left |

### Custom Messages

Services can define their own message types. Just use `send()` or `broadcast()`:

```javascript
// From user client
client.send('my_custom_type', { data: 'value' });

// From host to all users
host.broadcast('my_custom_type', { data: 'value' });
```

## Error Handling

Both host and client support error callbacks:

```javascript
const client = WSTun.createInstanceClient({
    // ...
    onError: (err) => {
        console.error('Connection error:', err.message);
        // Possible errors:
        // - Invalid or missing server auth token
        // - Invalid instance token
        // - Instance not found
        // - Connection failed
    }
});
```

## Tips

1. **Generate unique user IDs**: Use `WSTun.generateId()` or your own UUID generator
2. **Handle disconnections**: Users may disconnect at any time
3. **Validate data**: Don't trust data from other clients
4. **Use instance tokens**: For private rooms, always set an instance token
5. **Broadcast state changes**: Keep all users in sync by broadcasting updates
