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
- [x] Verify UI accessible via port-forward (`kubectl port-forward svc/kafbat-ui-kafka-ui -n kafka 8080:80`)

## Phase 4: Java Producers & Consumers
- [x] Build producer Docker image (Java 21, kafka-clients 3.9.0)
- [x] Build consumer Docker image (Java 21, kafka-clients 3.9.0)
- [x] Create producer StatefulSet (5 replicas)
- [x] Create consumer StatefulSet (5 replicas, same consumer group `demo-consumer-group`)
- [x] Store Kafka client credentials in Vault at `secret/kafka/clients`
- [x] Create ArgoCD Application for producers/consumers (`kafka-apps`)
- [x] Verify producers/consumers are connected and working

## Phase 5: Verification
- [x] All Strimzi CRDs installed (10 CRDs)
- [x] Operator pods: 2 Running (1 leader, 1 follower)
- [x] Kafka brokers: 3 Running
- [x] Kafka controllers: 3 Running
- [x] Kafbat UI: Running and accessible
- [x] Producers: 5 Running
- [x] Consumers: 5 Running
- [x] ArgoCD shows all apps Synced & Healthy (5 apps total)

## ArgoCD Applications Summary
| App | Type | Status |
|-----|------|--------|
| `strimzi-operator` | Helm (strimzi.io) | Synced & Healthy |
| `kafka-cluster` | Git (k8s/kafka/) | Synced & Healthy |
| `kafbat-ui` | Helm (kafbat) | Synced & Healthy |
| `kafka-apps` | Git (k8s/apps/) | Synced & Healthy |
| `vault-local` | Helm (hashicorp) | Synced & Healthy |

## Quick Access
- ArgoCD UI: `localhost:5000`
- Kafbat UI: `kubectl port-forward svc/kafbat-ui-kafka-ui -n kafka 8080:80` → `http://localhost:8080`
- Vault UI: `kubectl port-forward svc/vault-local-ui -n vault 8200:8200` → `http://localhost:8200`
