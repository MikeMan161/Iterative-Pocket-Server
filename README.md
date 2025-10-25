# Iterative-Pocket-Server
How to build & run on the class server

```bash
clone & compile
git clone https://github.com/<USER>/Iterative-Pocket-Server.git
cd Iterative-Pocket-Server/server/src
javac IterativeServer.java

run (choose any allowed port, e.g., 1025–4998)
java -cp . IterativeServer 0.0.0.0 <PORT>

## How to Use the Iterative Server (Client Developer Guide)

This explains how to talk to the server, how to test it, and what data the client needs to collect.

---

### 1. Server Location

The server is running on the class Linux VM.

- Server IP: `139.62.210.112`
- Port: `2201`

Your client must open a TCP connection to `139.62.210.112:2201`.

Note: Port 22 is SSH (for logging in with Bitvise). Do NOT use port 22 for the client/server socket test. We are using 2201, which is in the allowed range 1025–4998.

---

### 2. Protocol

How to talk to the server:

1. Open a TCP socket to `139.62.210.112:2201`.
2. Send ONE line with the operation you want, in UPPERCASE, followed by a newline `\n`.

   Valid operations:
   - `DATETIME`
   - `UPTIME`
   - `MEMORY`
   - `NETSTAT`
   - `USERS`
   - `PROCESSES`
   - `HELP`

   Example message sent to the socket:
   ```text
   UPTIME\n
