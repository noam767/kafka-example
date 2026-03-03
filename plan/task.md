# Kafka Cluster Deployment — Task Checklist

## Phase 1: Environment Identification
- [x] Read guides and skills
- [x] Verify K8s cluster (docker-desktop v1.34.1, single node, Ready)
- [x] Verify ArgoCD (7 pods healthy in `argocd` ns)
- [x] Verify Vault HA Raft (3 pods 1/1 Ready in `vault` ns)

## Phase 2: Strimzi Operator v0.50.1
- [x] Create `kafka` namespace
- [x] Apply all Strimzi CRDs (0.50.1 — 10 CRDs via Helm chart)
- [x] Deploy Strimzi Cluster Operator (2 replicas: leader + follower, leader election enabled)
- [x] Create ArgoCD Application for Strimzi operator (`strimzi-operator`)
- [x] Verify operator pods are Running

> **Note:** Strimzi 0.42.0 is incompatible with K8s 1.34.1 (fabric8 client issue). Using 0.50.1 instead.

## Phase 3: Kafka Cluster (v4.0.0, KRaft)
- [x] Create `Kafka` CR (3 brokers + 3 controllers, KafkaNodePool, StrimziPodSets, Kafka 4.0.0 & KRaft)
- [x] Store Kafka secrets in Vault (bootstrap, TLS certs) at `secret/kafka/bootstrap`
- [x] Create ArgoCD Application for Kafka cluster (`kafka-cluster`)
- [x] Verify Kafka pods are Running
- [x] Create test KafkaTopic CR (`demo-topic`, 5 partitions, RF=3)

## Phase 3.5: Kafbat UI
- [x] Deploy Kafbat UI (kafka-ui) as ArgoCD Application (`kafbat-ui`)
- [x] Configure to connect to demo-kafka bootstrap
- [x] Verify UI accessible via port-forward

## Phase 4: Java Producers & Consumers
- [x] Build producer Docker image (Java 21, kafka-clients 3.9.0)
- [x] Build consumer Docker image (Java 21, kafka-clients 3.9.0)
- [x] Create producer StatefulSet (5 replicas)
- [x] Create consumer StatefulSet (5 replicas, same consumer group `demo-consumer-group`)
- [x] Store Kafka client config in Vault at `secret/kafka/producer` and `secret/kafka/consumer`
- [x] Create ArgoCD Application for producers/consumers (`kafka-apps`)
- [x] Verify producers/consumers are connected and working

## Phase 5: KafkaConnect + MongoDB Sink
- [x] Deploy MongoDB standalone via Bitnami Helm ArgoCD Application (`mongodb`)
- [x] Store MongoDB credentials in Vault at `secret/kafka/mongodb`
- [x] Deploy KafkaConnect CR with MongoDB connector plugin (Strimzi build)
- [x] Deploy KafkaConnector CR (`mongodb-sink-connector`) sinking `demo-topic` → MongoDB
- [x] Create ArgoCD Application for KafkaConnect (`kafka-connect`)
- [x] Verify data flowing: producers → Kafka → KafkaConnect → MongoDB
- [x] Install MongoDB Compass for local GUI access

## Phase 6: Verification
- [x] All Strimzi CRDs installed (10 CRDs)
- [x] Operator pods: 2 Running (1 leader, 1 follower)
- [x] Kafka brokers: 3 Running
- [x] Kafka controllers: 3 Running
- [x] Kafbat UI: Running and accessible
- [x] Producers: 5 Running
- [x] Consumers: 5 Running
- [x] KafkaConnect: 1 Running with MongoDB sink connector
- [x] MongoDB: 1 Running, receiving sinked data
- [x] ArgoCD shows all 7 apps Synced & Healthy

## ArgoCD Applications Summary (7 total)
| App | Type | Status |
|-----|------|--------|
| `strimzi-operator` | Helm (strimzi.io) | Synced & Healthy |
| `kafka-cluster` | Git (k8s/kafka/) | Synced & Healthy |
| `kafbat-ui` | Helm (kafbat) | Synced & Healthy |
| `kafka-apps` | Git (k8s/apps/) | Synced & Healthy |
| `kafka-connect` | Git (k8s/connect/) | Synced & Healthy |
| `mongodb` | Helm (bitnami) | Synced & Healthy |
| `vault-local` | Helm (hashicorp) | Synced & Healthy |

## Vault Secrets
| Path | Contents |
|------|----------|
| `secret/kafka/bootstrap` | Bootstrap servers, TLS CA cert |
| `secret/kafka/producer` | Producer config (bootstrap, topic, interval) |
| `secret/kafka/consumer` | Consumer config (bootstrap, topic, group) |
| `secret/kafka/clients` | Client connection config |
| `secret/kafka/mongodb` | MongoDB connection URI, credentials |

## Quick Access
- **ArgoCD UI**: `localhost:5000`
- **Kafbat UI**: `kubectl port-forward svc/kafbat-ui-kafka-ui -n kafka 4000:80` → `http://localhost:4000`
- **Vault UI**: `kubectl port-forward svc/vault-local-ui -n vault 8200:8200` → `http://localhost:8200`
- **MongoDB**: `kubectl port-forward svc/mongodb -n kafka 27017:27017` → Compass: `mongodb://root:mongopass@localhost:27017/?authSource=admin`
