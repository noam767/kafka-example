# Vault Unseal After Pod Restart

When Vault pods restart (Docker Desktop sleep, node reboot, etc.), they come back **sealed**. With Shamir seal, you must manually unseal each pod using 3 of the 5 unseal keys generated during `vault operator init`.

---

## Step 1 — Check Which Pods Are Sealed

```bash
kubectl get pods -n vault
```

Sealed pods show `0/1 READY`. You can confirm with:

```bash
kubectl exec -n vault vault-local-0 -- vault status -format=json | jq '.sealed'
kubectl exec -n vault vault-local-1 -- vault status -format=json | jq '.sealed'
kubectl exec -n vault vault-local-2 -- vault status -format=json | jq '.sealed'
```

---

## Step 2 — Set Your Unseal Keys

Store 3 of your 5 unseal keys in shell variables:

```bash
U1="<unseal-key-1>"
U2="<unseal-key-2>"
U3="<unseal-key-3>"
```

---

## Step 3 — Unseal Each Sealed Pod

For each sealed pod, run all 3 unseal commands. Each command increments the unseal progress (1/3 → 2/3 → unsealed).

```bash
# Unseal vault-local-1
kubectl exec -n vault vault-local-1 -- vault operator unseal "$U1"
kubectl exec -n vault vault-local-1 -- vault operator unseal "$U2"
kubectl exec -n vault vault-local-1 -- vault operator unseal "$U3"

# Unseal vault-local-2
kubectl exec -n vault vault-local-2 -- vault operator unseal "$U1"
kubectl exec -n vault vault-local-2 -- vault operator unseal "$U2"
kubectl exec -n vault vault-local-2 -- vault operator unseal "$U3"
```

If vault-local-0 is also sealed, unseal it first (it's the Raft leader).

---

## Step 4 — Verify

```bash
kubectl get pods -n vault
```

All 3 pods should show `1/1 READY`. Verify the Raft cluster:

```bash
kubectl exec -n vault vault-local-0 -- vault login <root-token>
kubectl exec -n vault vault-local-0 -- vault operator raft list-peers
```

Expected: 3 nodes — 1 leader, 2 followers.

---

## One-Liner (Unseal All Sealed Pods)

```bash
U1="<key1>" U2="<key2>" U3="<key3>" && \
for pod in vault-local-0 vault-local-1 vault-local-2; do \
  echo "=== Unsealing $pod ===" && \
  kubectl exec -n vault $pod -- vault operator unseal "$U1" && \
  kubectl exec -n vault $pod -- vault operator unseal "$U2" && \
  kubectl exec -n vault $pod -- vault operator unseal "$U3"; \
done
```

---

## Why This Happens

Vault uses **Shamir's Secret Sharing** by default. The master key that decrypts the encryption key is split into 5 shares (threshold 3). Vault never persists the master key — it's reconstructed in memory from the unseal keys. When a pod restarts, memory is wiped, so it starts sealed.

To avoid manual unsealing, consider migrating to **auto-unseal** using a cloud KMS or Transit secrets engine.
