# KafkaConnect MongoDB Sink — Deployment Guide

## Architecture

```
Producers (5 pods) → demo-topic → KafkaConnect → MongoDB
                                   (MongoSinkConnector)
```

Data flows from 5 Java producer pods into `demo-topic` (5 partitions, RF=3), then KafkaConnect sinks every message into MongoDB collection `kafka_sink.demo_topic_data`.

---

## Components

| Component | Resource | Namespace |
|-----------|----------|-----------|
| KafkaConnect | `KafkaConnect/demo-connect` | kafka |
| MongoDB Sink | `KafkaConnector/mongodb-sink-connector` | kafka |
| MongoDB | Bitnami Helm (standalone) | kafka |
| ArgoCD App | `kafka-connect` (Git: k8s/connect/) | argocd |
| ArgoCD App | `mongodb` (Helm: bitnami/mongodb) | argocd |

---

## How the KafkaConnect Image Build Works

Strimzi builds a custom KafkaConnect Docker image at deploy time using the `build` section in the KafkaConnect CR:

1. Downloads the MongoDB connector JAR from Maven Central (`org.mongodb.kafka:mongo-kafka-connect:1.13.1`)
2. Layers it on top of the Strimzi Kafka base image (`quay.io/strimzi/kafka:0.50.1-kafka-4.0.0`)
3. Pushes the built image to `ttl.sh` (ephemeral registry, no auth needed)
4. Starts the KafkaConnect pod using the built image

The build happens as a Kubernetes pod (`demo-connect-connect-build`) and can take 5-10 minutes depending on network speed.

---

## Key Configuration Files

### KafkaConnect CR — `k8s/connect/kafka-connect.yaml`

```yaml
spec:
  version: "4.0.0"
  bootstrapServers: demo-kafka-kafka-bootstrap.kafka.svc.cluster.local:9092
  build:
    output:
      type: docker
      image: ttl.sh/demo-connect-mongodb:24h
    plugins:
      - name: mongodb-connector
        artifacts:
          - type: maven
            group: org.mongodb.kafka
            artifact: mongo-kafka-connect
            version: 1.13.1
```

### KafkaConnector CR — `k8s/connect/kafka-connector-mongodb.yaml`

```yaml
spec:
  class: com.mongodb.kafka.connect.MongoSinkConnector
  tasksMax: 1
  config:
    topics: demo-topic
    connection.uri: "mongodb://root:mongopass@mongodb.kafka.svc.cluster.local:27017"
    database: kafka_sink
    collection: demo_topic_data
```

---

## Vault Secrets

MongoDB credentials are stored in Vault:

```bash
kubectl exec -n vault vault-local-0 -- vault kv get secret/kafka/mongodb
```

---

## Verification Commands

### Check KafkaConnect status
```bash
kubectl get kafkaconnect -n kafka
kubectl get kafkaconnector -n kafka
```

### Check connector logs
```bash
kubectl logs -n kafka -l strimzi.io/cluster=demo-connect --tail=20
```

### Query MongoDB for sinked data
```bash
kubectl exec -n kafka <mongodb-pod> -- mongosh \
  "mongodb://root:mongopass@localhost:27017/kafka_sink?authSource=admin" \
  --eval "db.demo_topic_data.countDocuments({})"
```

### View sample documents
```bash
kubectl exec -n kafka <mongodb-pod> -- mongosh \
  "mongodb://root:mongopass@localhost:27017/kafka_sink?authSource=admin" \
  --eval "db.demo_topic_data.find().sort({_id:-1}).limit(5).toArray()"
```

---

## Troubleshooting

### Build pod stuck pushing
The ttl.sh push can be slow. Check logs:
```bash
kubectl logs <build-pod> -n kafka --tail=5
```
If stuck for >15 minutes, delete the KafkaConnect CR and re-apply.

### Connector not sinking data
Check connector status:
```bash
kubectl get kafkaconnector mongodb-sink-connector -n kafka -o yaml
```
Look for `status.conditions` — common issues:
- MongoDB auth failure → check `connection.uri`
- Topic doesn't exist → verify `demo-topic` exists
- Converter mismatch → ensure key/value converters match producer format

### ttl.sh image expired
Images on ttl.sh expire after 24h. If the connect pod gets evicted, delete and re-create the KafkaConnect CR to trigger a new build.
