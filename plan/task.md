# Kafka Cluster Deployment — Task Checklist

## Phase 1: Environment Identification
- [x] Read guides and skills
- [x] Verify K8s cluster (docker-desktop v1.34.1, single node, Ready)
- [x] Verify ArgoCD (7 pods healthy in `argocd` ns)
- [x] Verify Vault HA Raft (3 pods 1/1 Ready in `vault` ns)

## Phase 2: Strimzi Operator v0.50.1
- [x] Create `kafka` namespace
- [x] Apply all Strimzi CRDs (0.50.1 — via Helm chart)
- [x] Deploy Strimzi Cluster Operator (2 replicas: leader + follower, leader election enabled)
- [x] Create ArgoCD Application for Strimzi operator
- [x] Verify operator pods are Running

> **Note:** Strimzi 0.42.0 is incompatible with K8s 1.34.1 (fabric8 client issue). Using 0.50.1 instead.

## Phase 3: Kafka Cluster (v4.0.0, KRaft)
- [x] Create `Kafka` CR (3 brokers + 3 controllers, KafkaNodePool, StrimziPodSets, Kafka 4.0.0 & KRaft)
- [x] Store Kafka secrets in Vault (bootstrap, TLS certs)
- [ ] Create ArgoCD Application for Kafka cluster
- [x] Verify Kafka pods are Running
- [x] Create test KafkaTopic CR (`demo-topic`, 5 partitions, RF=3)

## Phase 3.5: Kafbat UI
- [ ] Deploy Kafbat UI (kafka-ui) as ArgoCD Application
- [ ] Configure to connect to demo-kafka bootstrap
- [ ] Verify UI accessible via port-forward

## Phase 4: Java Producers & Consumers
- [x] Build producer Docker image (Java 21, kafka-clients 3.9.0)
- [ ] Build consumer Docker image (Java 21, kafka-clients 3.9.0)
- [ ] Create producer StatefulSet (5 replicas)
- [ ] Create consumer StatefulSet (5 replicas, same consumer group `demo-consumer-group`)
- [ ] Store Kafka credentials as Vault secrets
- [ ] Create ArgoCD Application for producers/consumers
- [ ] Verify producers/consumers are connected and working

## Phase 5: Verification
- [x] All Strimzi CRDs installed (10 CRDs)
- [x] Operator pods: 2 Running (1 leader, 1 follower)
- [x] Kafka brokers: 3 Running
- [x] Kafka controllers: 3 Running
- [ ] Kafbat UI: Running and accessible
- [ ] Producers: 5 Running
- [ ] Consumers: 5 Running
- [ ] ArgoCD shows all apps Synced & Healthy
- [ ] Create walkthrough.md
