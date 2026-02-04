# WSTun Marketplace

## Overview

The WSTun Marketplace allows users to discover, install, and manage services from external sources. This document explains how to create a marketplace server and publish services.

## Marketplace API

A marketplace is simply an HTTP server that provides a specific API. WSTun clients fetch service information and files from the marketplace URL.

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `{baseUrl}/list` | GET | List all available services |
| `{baseUrl}/service/{name}.json` | GET | Get service manifest |
| `{baseUrl}/service/{name}/{file}` | GET | Download service file |

### Example Marketplace Structure

```
https://example.com/wstun/marketplace/
├── list                           # Service list JSON
└── service/
    ├── myservice.json             # Service manifest
    └── myservice/
        ├── index.html             # Service controller
        └── main.html              # User client
```

## Creating a Marketplace

### 1. Service List (`/list`)

Returns a JSON array of available services:

```json
[
    {
        "name": "myservice",
        "displayName": "My Awesome Service",
        "description": "A great service for doing things",
        "version": "1.0.0",
        "author": "Your Name"
    },
    {
        "name": "anotherservice",
        "displayName": "Another Service",
        "description": "Does other things",
        "version": "2.1.0",
        "author": "Someone Else"
    }
]
```

### 2. Service Manifest (`/service/{name}.json`)

Each service needs a manifest file that describes its structure:

```json
{
    "name": "myservice",
    "displayName": "My Awesome Service",
    "description": "A great service for doing things",
    "version": "1.0.0",
    "author": "Your Name",
    "icon": "https://example.com/icon.png",
    "endpoints": [
        {
            "path": "/service",
            "file": "index.html",
            "type": "service"
        },
        {
            "path": "/main",
            "file": "main.html",
            "type": "client"
        }
    ]
}
```

#### Manifest Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique service identifier (alphanumeric, lowercase) |
| `displayName` | No | Human-readable name |
| `description` | No | Short description of the service |
| `version` | No | Semantic version string |
| `author` | No | Author name or organization |
| `icon` | No | URL to service icon |
| `endpoints` | Yes | Array of endpoint definitions |

#### Endpoint Fields

| Field | Required | Description |
|-------|----------|-------------|
| `path` | Yes | URL path (e.g., `/service`, `/main`) |
| `file` | Yes | Filename to serve for this path |
| `type` | Yes | `service` (controller) or `client` (user) |
| `contentType` | No | MIME type (auto-detected from extension if not set) |

### 3. Service Files (`/service/{name}/{file}`)

The actual HTML/JS files for your service. These should use `libwstun.js` for communication.

## Example: Creating a Simple Service

### 1. Create the Service Controller (`index.html`)

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>My Service - Controller</title>
</head>
<body>
    <h1>My Service</h1>
    <button onclick="createRoom()">Create Room</button>
    <div id="status"></div>
    <div id="users"></div>

    <script src="/libwstun.js"></script>
    <script>
let host = null;

function createRoom() {
    host = WSTun.createInstanceHost({
        service: 'myservice',
        name: 'My Room',
        
        onInstanceCreated: (payload) => {
            document.getElementById('status').innerHTML = 
                `Room created! Join URL: <a href="/myservice/main?instance=${payload.uuid}">/myservice/main?instance=${payload.uuid}</a>`;
        },
        
        onMessage: (msg) => {
            if (msg.type === 'client_connected') {
                updateUsers(msg.payload.userId, true);
            } else if (msg.type === 'client_disconnected') {
                updateUsers(msg.payload.userId, false);
            } else if (msg.type === 'custom_message') {
                // Handle custom messages from users
                console.log('Received:', msg.payload);
                // Broadcast to all users
                host.broadcast('custom_message', msg.payload);
            }
        }
    });
    
    host.connect();
}

const users = new Set();
function updateUsers(userId, joined) {
    if (joined) users.add(userId);
    else users.delete(userId);
    document.getElementById('users').innerHTML = 
        Array.from(users).map(u => `<div>${u}</div>`).join('');
}
    </script>
</body>
</html>
```

### 2. Create the User Client (`main.html`)

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>My Service</title>
</head>
<body>
    <h1>My Service</h1>
    <div id="status">Loading...</div>
    <input type="text" id="message" placeholder="Type a message">
    <button onclick="sendMessage()">Send</button>
    <div id="messages"></div>

    <script src="/libwstun.js"></script>
    <script>
const params = new URLSearchParams(location.search);
const instanceUuid = params.get('instance');

if (!instanceUuid) {
    document.getElementById('status').textContent = 'No room specified';
} else {
    const client = WSTun.createInstanceClient({
        service: 'myservice',
        instanceUuid: instanceUuid,
        
        onJoined: (payload) => {
            document.getElementById('status').textContent = 
                'Connected to ' + payload.instanceName;
        },
        
        onMessage: (msg) => {
            if (msg.type === 'custom_message') {
                const div = document.createElement('div');
                div.textContent = msg.payload.text;
                document.getElementById('messages').appendChild(div);
            }
        },
        
        onKicked: () => {
            alert('You were kicked!');
            location.reload();
        }
    });
    
    client.connect();
    
    window.sendMessage = function() {
        const text = document.getElementById('message').value;
        client.send('custom_message', { text });
        document.getElementById('message').value = '';
    };
}
    </script>
</body>
</html>
```

### 3. Create the Manifest (`myservice.json`)

```json
{
    "name": "myservice",
    "displayName": "My Custom Service",
    "description": "A simple example service",
    "version": "1.0.0",
    "author": "Your Name",
    "endpoints": [
        {
            "path": "/service",
            "file": "index.html",
            "type": "service"
        },
        {
            "path": "/main",
            "file": "main.html",
            "type": "client"
        }
    ]
}
```

### 4. Update the Service List (`list`)

```json
[
    {
        "name": "myservice",
        "displayName": "My Custom Service",
        "description": "A simple example service",
        "version": "1.0.0",
        "author": "Your Name"
    }
]
```

## Hosting a Marketplace

### Option 1: Static File Server

Host files on any static file server (Apache, Nginx, S3, GitHub Pages):

```
/marketplace/
  list                 # Service list JSON
  service/
    myservice.json     # Manifest
    myservice/
      index.html       # Controller
      main.html        # Client
```

Make sure CORS headers allow access from the WSTun server.

### Option 2: Dynamic Server

Create a dynamic server (Node.js, Python, etc.) that generates the list and serves files:

```javascript
// Express.js example
const express = require('express');
const app = express();

// Enable CORS
app.use((req, res, next) => {
    res.header('Access-Control-Allow-Origin', '*');
    next();
});

// Service list
app.get('/marketplace/list', (req, res) => {
    res.json([
        { name: 'myservice', displayName: 'My Service', ... }
    ]);
});

// Service manifest
app.get('/marketplace/service/:name.json', (req, res) => {
    res.sendFile(`./services/${req.params.name}/manifest.json`);
});

// Service files
app.get('/marketplace/service/:name/:file', (req, res) => {
    res.sendFile(`./services/${req.params.name}/${req.params.file}`);
});

app.listen(9090);
```

## Installing Services from Marketplace

### Via Admin UI

1. Open the WSTun server in a browser
2. Click "Service Manager" (or go to `/admin`)
3. Go to the "Marketplace" tab
4. Enter the marketplace URL
5. Click "Fetch" to see available services
6. Click "Install" on the desired service

### Via API

```javascript
// POST /_api/marketplace/install
fetch('/_api/marketplace/install', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        url: 'https://example.com/wstun/marketplace',
        name: 'myservice'
    })
});
```

## Best Practices

1. **Use semantic versioning**: Makes it easy to track updates
2. **Write clear descriptions**: Help users understand what your service does
3. **Test thoroughly**: Ensure your service works with the WSTun server
4. **Include documentation**: Add comments or a README in your service
5. **Handle errors gracefully**: Show user-friendly error messages
6. **Use HTTPS**: Secure your marketplace server
7. **Add CORS headers**: Allow cross-origin requests from WSTun servers

## Security Considerations

1. **Validate service names**: Only alphanumeric and lowercase
2. **Sanitize file paths**: Prevent directory traversal attacks
3. **Review code**: Marketplace services can run arbitrary JavaScript
4. **Use HTTPS**: Encrypt data in transit
5. **Implement rate limiting**: Prevent abuse of your marketplace

## Troubleshooting

### "Failed to fetch marketplace"
- Check the marketplace URL is correct
- Ensure CORS headers are set
- Verify the `/list` endpoint returns valid JSON

### "Failed to install service"
- Check the manifest is valid JSON
- Verify all endpoint files exist
- Check file permissions on the server

### Service doesn't appear after install
- Refresh the service list
- Check the browser console for errors
- Verify the service files were downloaded correctly
