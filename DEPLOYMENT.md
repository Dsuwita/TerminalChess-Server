# Terminal Chess - Deployment Summary

## Server Details
- **IP Address**: 209.38.75.155
- **Port**: 5000
- **Status**: Running as systemd service

## Server Management

### Check Status
```bash
ssh root@209.38.75.155 "systemctl status terminalchess"
```

### View Logs
```bash
ssh root@209.38.75.155 "journalctl -u terminalchess -f"
```

### Restart Server
```bash
ssh root@209.38.75.155 "systemctl restart terminalchess"
```

### Stop Server
```bash
ssh root@209.38.75.155 "systemctl stop terminalchess"
```

## Redeploy Updates

```bash
cd /home/dylan/terminalChess/server
mvn clean package
scp target/server-0.1.0.jar root@209.38.75.155:/opt/terminalchess/
ssh root@209.38.75.155 "systemctl restart terminalchess"
```

## Client Connection

### Quick Match (Auto-pairing)
```bash
python3 client/client.py --host 209.38.75.155 --name YourName
```

### Create Private Room
```bash
python3 client/client.py --host 209.38.75.155 --name YourName --create
```

### Join Private Room
```bash
python3 client/client.py --host 209.38.75.155 --name YourName --room ROOMKEY
```

## Server Limits
- **Max Active Matches**: 20
- **Max Pending Rooms**: 5
- **Room Expiry**: 5 minutes
- Overflow players are queued

## Installed Components
- Java 17 (OpenJDK)
- Systemd service (auto-restart on failure)
- Firewall rule (port 5000 open)
