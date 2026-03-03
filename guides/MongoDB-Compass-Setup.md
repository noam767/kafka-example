# MongoDB Compass — Installation & Connection Guide

## Install MongoDB Compass

Open **PowerShell as Administrator** and run:

```powershell
choco install mongodb-compass -y
```

Compass installs to `C:\Program Files\MongoDB Compass\MongoDBCompass.exe`.

---

## Port-Forward MongoDB

MongoDB runs inside the K8s cluster. To access it locally:

```bash
kubectl port-forward svc/mongodb -n kafka 27017:27017
```

This forwards `localhost:27017` → MongoDB pod in the kafka namespace.

> **Tip:** Run this in a separate terminal — it stays open as long as you need access.

---

## Connect with Compass

1. Open MongoDB Compass
2. Paste the connection string:

```
mongodb://root:mongopass@localhost:27017/?authSource=admin
```

3. Click **Connect**

---

## View Kafka Sink Data

Once connected:

1. Click on the **kafka_sink** database
2. Click on the **demo_topic_data** collection
3. You'll see documents flowing in from the KafkaConnect MongoDB sink connector

Each document looks like:

```json
{
  "_id": "ObjectId(...)",
  "producer": "kafka-producer-0",
  "seq": 115,
  "ts": 1772543234990
}
```

---

## Useful Queries in Compass

**Count all documents:**
Filter: `{}` → check document count in the bottom bar

**Find messages from a specific producer:**
```json
{ "producer": "kafka-producer-2" }
```

**Sort by newest first:**
Sort: `{ "_id": -1 }`

**Find messages in a time range (epoch ms):**
```json
{ "ts": { "$gte": 1772540000000 } }
```

---

## Alternative: mongosh CLI

If you prefer the terminal:

```bash
mongosh "mongodb://root:mongopass@localhost:27017/kafka_sink?authSource=admin"
```

Then:

```js
db.demo_topic_data.countDocuments({})
db.demo_topic_data.find().sort({_id:-1}).limit(5)
```

---

## Credentials (stored in Vault)

| Key | Value |
|-----|-------|
| Connection URI | `mongodb://root:mongopass@localhost:27017` |
| Auth Source | `admin` |
| Database | `kafka_sink` |
| Collection | `demo_topic_data` |
| Vault path | `secret/kafka/mongodb` |
