# ZooKeeper Ensemble — Deployment & Config Watch Guide

## Architecture

```
ZooKeeper Ensemble (3 nodes)
  ├── /kafka-platform/config/producer  ← watched by zk-config-watcher
  ├── /kafka-platform/config/consumer  ← watched by zk-config-watcher
  └── /kafka-platform/config/connect   ← watched by zk-config-watcher

CronJob (every 6h) → dumps all znodes → MongoDB kafka_sink.zk_snapshots
```

---

## Components

| Component | Resource | Namespace | ArgoCD App |
|-----------|----------|-----------|------------|
| ZooKeeper (3 nodes) | Bitnami Helm StatefulSet | kafka | `zookeeper` |
| Config Watcher | Deployment (Python kazoo) | kafka | `zookeeper-apps` |
| Snapshot Backup | CronJob (Python kazoo+pymongo) | kafka | `zookeeper-apps` |

---

## ZooKeeper Deployment

Deployed via Bitnami Helm chart v13.8.7 (ZooKeeper 3.9.3) through ArgoCD.

**Image:** `bitnamilegacy/zookeeper:3.9.3` (Bitnami deprecated original images Aug 2025)

**Pods:**
- `zookeeper-0`, `zookeeper-1`, `zookeeper-2`
- Headless service: `zookeeper-headless.kafka.svc.cluster.local`
- Client service: `zookeeper.kafka.svc.cluster.local:2181`

**Auth:** Digest scheme — `zkuser`/`zkpass123` (client), `zkadmin`/`zkadminpass123` (server)

---

## Config Watch (Observer Pattern)

The `zk-config-watcher` Deployment uses Python's [kazoo](https://kazoo.readthedocs.io/) library with `DataWatch` and `ChildrenWatch` to monitor config znodes in real-time.

**Watched znodes:**
- `/kafka-platform/config/producer` — Kafka producer settings
- `/kafka-platform/config/consumer` — Kafka consumer settings
- `/kafka-platform/config/connect` — KafkaConnect/MongoDB settings

**How it works:**
1. Init container (zkCli) creates znodes if they don't exist
2. Main container (Python) uses kazoo `DataWatch` for each znode
3. When a znode value changes, the watch fires and logs the old/new value
4. Watches are persistent — they survive reconnections

### View watcher logs

```bash
kubectl logs -n kafka -l app.kubernetes.io/name=zk-config-watcher -f
```

### Trigger a config change (test watch)

```bash
# Exec into the watcher pod
kubectl exec -n kafka deployment/zk-config-watcher -c watcher -- \
  python3 -c "
from kazoo.client import KazooClient
import os
zk = KazooClient(hosts=os.environ['ZK_CONNECT'])
zk.start()
zk.set('/kafka-platform/config/producer', b'bootstrap=demo-kafka-kafka-bootstrap.kafka.svc.cluster.local:9092,topic=demo-topic,interval_ms=500')
zk.stop()
print('Config updated — check watcher logs')
"
```

---

## Snapshot Backup CronJob

Runs every 6 hours, dumps the entire ZK znode tree into MongoDB.

**Schedule:** `0 */6 * * *` (00:00, 06:00, 12:00, 18:00 UTC)

**Target:** MongoDB `kafka_sink.zk_snapshots`

**Retention:** Last 20 backups

### Trigger manual backup

```bash
kubectl create job zk-backup-manual --from=cronjob/zk-snapshot-backup -n kafka
```

### View backup in MongoDB

```bash
kubectl port-forward svc/mongodb -n kafka 27017:27017
```

Then in Compass or mongosh:

```js
use kafka_sink
db.zk_snapshots.find().sort({timestamp: -1}).limit(1).pretty()
```

### Backup document structure

```json
{
  "backup_id": "zk-backup-20260304-125239",
  "timestamp": "2026-03-04T12:52:39Z",
  "znode_count": 6,
  "snapshot_data": [
    {"path": "/", "data": "", "version": 0, ...},
    {"path": "/kafka-platform", "data": "init", ...},
    {"path": "/kafka-platform/config/producer", "data": "bootstrap=...,topic=demo-topic,interval_ms=2000", ...}
  ],
  "metadata": {
    "zk_host": "zookeeper-0.zookeeper-headless...",
    "created_by": "zk-snapshot-backup-cronjob",
    "version": "2.0"
  }
}
```

---

## Vault Secrets

```bash
kubectl exec -n vault vault-local-0 -- vault kv get secret/kafka/zookeeper
```

| Key | Value |
|-----|-------|
| `zk_client_user` | `zkuser` |
| `zk_client_password` | `zkpass123` |
| `zk_admin_user` | `zkadmin` |
| `zk_admin_password` | `zkadminpass123` |
| `zk_connect` | `zookeeper-0.zookeeper-headless.kafka.svc.cluster.local:2181,...` |

---

## Verification Commands

```bash
# Check ZooKeeper pods
kubectl get pods -n kafka -l app.kubernetes.io/name=zookeeper

# Check watcher
kubectl logs -n kafka -l app.kubernetes.io/name=zk-config-watcher -c watcher --tail=20

# Check backup CronJob
kubectl get cronjob zk-snapshot-backup -n kafka

# Check ArgoCD apps
kubectl get app zookeeper zookeeper-apps -n argocd

# zkCli into ZK (from any ZK pod)
kubectl exec -n kafka zookeeper-0 -- /opt/bitnami/zookeeper/bin/zkCli.sh -server localhost:2181 ls -R /kafka-platform
```

---

## Troubleshooting

### ZooKeeper image pull error
Bitnami images were removed from Docker Hub. Use `bitnamilegacy/zookeeper:3.9.3` with `global.security.allowInsecureImages: true` in Helm values.

### Watcher not detecting changes
Check that znodes exist: `kubectl exec zookeeper-0 -n kafka -- /opt/bitnami/zookeeper/bin/zkCli.sh -server localhost:2181 ls /kafka-platform/config`

### Backup CronJob failing
Check job logs: `kubectl logs -n kafka -l job-name=<job-name>`
Common issue: MongoDB not reachable — verify `mongodb.kafka.svc.cluster.local:27017` is up.
