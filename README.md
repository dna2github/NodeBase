# NodeBase
<img src="https://raw.githubusercontent.com/wiki/dna2github/NodeBase/images/log.png" />

Platform to Build Sharable Application for Android

Running Websocket application over Wifi and share with your friends.

- WSTun: it is a base server on http/websocket with netty; and project name is "WSTun"

The design is changed, we never use a pre-built binary of NodeJS to run server anymore.

We run a native http/websocket wit netty.

Any JS client can register itself as a service so that it can be a control center of data.

Others can connect as clients of the service and do more flexible things like file sharing, board gaming!

It is also a project enjoy vibe coding!
