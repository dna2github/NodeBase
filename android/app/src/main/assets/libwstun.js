/**
 * WSTun Client Library v2.0
 * Supports: Server auth, Service instances, User management
 */
(function(global) {
'use strict';

const WSTun = {
    version: '1.0.0',
    
    /** Create instance host (creates and manages an instance) */
    createInstanceHost: function(options) { return new InstanceHost(options); },
    
    /** Create instance client (joins an existing instance) */
    createInstanceClient: function(options) { return new InstanceClient(options); },
    
    /** Generate unique ID */
    generateId: function() { return Date.now().toString(36) + Math.random().toString(36).substr(2, 6); },
    
    /** Build WebSocket URL */
    buildWsUrl: function(serverToken) {
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        let url = protocol + '//' + location.host + '/ws';
        if (serverToken) url += '?token=' + encodeURIComponent(serverToken);
        return url;
    }
};

/** Base WebSocket client */
class BaseClient {
    constructor(options) {
        this.service = options.service;
        this.serverToken = options.serverToken || null;
        this.onOpen = options.onOpen || (() => {});
        this.onMessage = options.onMessage || (() => {});
        this.onClose = options.onClose || (() => {});
        this.onError = options.onError || (() => {});
        this.ws = null;
        this.connected = false;
    }
    
    connect() {
        try { this.ws = new WebSocket(WSTun.buildWsUrl(this.serverToken)); }
        catch (err) { this.onError(err); return; }
        this.ws.onopen = () => { this.connected = true; this.onOpen(); this._onConnected(); };
        this.ws.onmessage = (e) => { try { this._handleMessage(JSON.parse(e.data)); } catch(err) { console.error(err); } };
        this.ws.onclose = () => { this.connected = false; this.onClose(); };
        this.ws.onerror = (err) => { this.onError(err); };
    }
    
    disconnect() { if (this.ws) { this.ws.close(); this.ws = null; } }
    
    send(type, payload) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({ type, service: this.service, payload }));
        }
    }
    
    _onConnected() {}
    _handleMessage(msg) { this.onMessage(msg); }
}

/**
 * Instance Host - Creates and manages a service instance (room)
 * @param {Object} options
 * @param {string} options.service - Service type (e.g., 'fileshare', 'chat')
 * @param {string} options.name - Instance name (room name)
 * @param {string} [options.serverToken] - Server auth token
 * @param {string} [options.instanceToken] - Token users need to join
 * @param {string} [options.reclaimUuid] - UUID of instance to reclaim (for reconnection)
 * @param {function} [options.onInstanceCreated] - Called when instance is created
 * @param {function} [options.onInstanceReclaimed] - Called when instance is reclaimed
 */
class InstanceHost extends BaseClient {
    constructor(options) {
        super(options);
        this.instanceName = options.name || 'Room';
        this.instanceToken = options.instanceToken || null;
        this.reclaimUuid = options.reclaimUuid || null;
        this.onInstanceCreated = options.onInstanceCreated || (() => {});
        this.onInstanceReclaimed = options.onInstanceReclaimed || options.onInstanceCreated || (() => {});
        this.instanceUuid = null;
    }
    
    _onConnected() {
        if (this.reclaimUuid) {
            // Try to reclaim an existing instance
            this.send('reclaim_instance', {
                uuid: this.reclaimUuid,
                token: this.instanceToken,
                server_token: this.serverToken
            });
        } else {
            // Create new instance
            this.send('create_instance', {
                name: this.instanceName,
                token: this.instanceToken,
                server_token: this.serverToken
            });
        }
    }
    
    _handleMessage(msg) {
        if (msg.type === 'instance_created' && msg.payload?.success) {
            this.instanceUuid = msg.payload.uuid;
            this.onInstanceCreated(msg.payload);
        } else if (msg.type === 'instance_reclaimed' && msg.payload?.success) {
            this.instanceUuid = msg.payload.uuid;
            this.onInstanceReclaimed(msg.payload);
        } else if (msg.type === 'instance_reclaimed' && !msg.payload?.success) {
            // Reclaim failed, create new instance instead
            this.reclaimUuid = null;
            this.send('create_instance', {
                name: this.instanceName,
                token: this.instanceToken,
                server_token: this.serverToken
            });
        } else if (msg.type === 'error') {
            this.onError(new Error(msg.payload?.message || 'Unknown error'));
        } else {
            this.onMessage(msg);
        }
    }
    
    /** Broadcast message to all users in instance */
    broadcast(type, payload) {
        if (this.instanceUuid) {
            payload = payload || {};
            payload.instanceUuid = this.instanceUuid;
            this.send(type, payload);
        }
    }
    
    /** Kick a user from instance */
    kickUser(userId) {
        this.send('kick_client', { userId, instanceUuid: this.instanceUuid });
    }
}

/**
 * Instance Client - Joins an existing instance
 * @param {Object} options
 * @param {string} options.service - Service type
 * @param {string} options.instanceUuid - Instance UUID to join
 * @param {string} [options.serverToken] - Server auth token
 * @param {string} [options.instanceToken] - Instance access token
 * @param {string} [options.userId] - User ID (auto-generated if not provided)
 * @param {function} [options.onJoined] - Called when joined instance
 * @param {function} [options.onKicked] - Called when kicked from instance
 */
class InstanceClient extends BaseClient {
    constructor(options) {
        super(options);
        this.instanceUuid = options.instanceUuid;
        this.instanceToken = options.instanceToken || null;
        this.userId = options.userId || WSTun.generateId();
        this.onJoined = options.onJoined || (() => {});
        this.onKicked = options.onKicked || (() => {});
        this.instanceName = null;
    }
    
    _onConnected() {
        this.send('join_instance', {
            uuid: this.instanceUuid,
            userId: this.userId,
            token: this.instanceToken
        });
    }
    
    _handleMessage(msg) {
        if (msg.type === 'ack' && msg.payload?.success && msg.payload?.instanceUuid) {
            this.instanceName = msg.payload.instanceName;
            this.onJoined(msg.payload);
        } else if (msg.type === 'kick') {
            this.onKicked(msg.payload);
            this.disconnect();
        } else if (msg.type === 'error') {
            this.onError(new Error(msg.payload?.message || 'Unknown error'));
        } else {
            this.onMessage(msg);
        }
    }
}

/**
 * List available instances for a service
 * @param {string} service - Service type
 * @param {string} [serverToken] - Server auth token
 * @returns {Promise<Array>} List of instances
 */
WSTun.listInstances = function(service, serverToken) {
    return new Promise((resolve, reject) => {
        let resolved = false;
        let ws;
        
        try {
            ws = new WebSocket(WSTun.buildWsUrl(serverToken));
        } catch(err) {
            reject(err);
            return;
        }
        
        const cleanup = () => {
            resolved = true;
            if (ws && ws.readyState !== WebSocket.CLOSED) {
                try { ws.close(); } catch(e) {}
            }
        };
        
        ws.onopen = () => {
            if (resolved) return;
            ws.send(JSON.stringify({ type: 'list_instances', service, payload: {} }));
        };
        ws.onmessage = (e) => {
            if (resolved) return;
            try {
                const msg = JSON.parse(e.data);
                if (msg.type === 'instance_list') {
                    cleanup();
                    resolve(msg.payload?.instances || []);
                } else if (msg.type === 'error') {
                    cleanup();
                    reject(new Error(msg.payload?.message || msg.payload?.error || 'Failed to list instances'));
                }
            } catch(err) { 
                cleanup();
                reject(err); 
            }
        };
        ws.onerror = (err) => { 
            if (resolved) return;
            cleanup();
            reject(new Error('WebSocket connection failed')); 
        };
        ws.onclose = () => {
            if (resolved) return;
            cleanup();
            resolve([]); // Return empty list if connection closes without response
        };
        
        // Shorter timeout (3 seconds) to avoid long waits
        setTimeout(() => { 
            if (resolved) return;
            cleanup();
            resolve([]); // Return empty list on timeout instead of rejecting
        }, 3000);
    });
};

// Legacy support - createService and createClient still work for simple cases
WSTun.createService = function(options) { return new InstanceHost(options); };
WSTun.createClient = function(options) {
    // If instanceUuid provided, use InstanceClient, otherwise fallback
    if (options.instanceUuid) return new InstanceClient(options);
    // Simple client without instance support (legacy)
    const client = new BaseClient(options);
    client.userId = options.userId || WSTun.generateId();
    client.onRegistered = options.onRegistered || (() => {});
    client.onKicked = options.onKicked || (() => {});
    client._onConnected = function() {
        this.send('client_register', { clientType: this.service, userId: this.userId, auth_token: options.serviceToken });
    };
    client._handleMessage = function(msg) {
        if (msg.type === 'ack' && msg.payload?.success) { this.onRegistered(msg.payload); }
        else if (msg.type === 'kick') { this.onKicked(msg.payload); this.disconnect(); }
        else if (msg.type === 'error') { this.onError(new Error(msg.payload?.message || 'Error')); }
        else { this.onMessage(msg); }
    };
    return client;
};
WSTun.generateUserId = WSTun.generateId;

global.WSTun = WSTun;
})(typeof window !== 'undefined' ? window : this);
