# Vault HA Raft on Docker Desktop — Init & Unseal Runbook

## Prerequisites

- Docker Desktop running with Kubernetes enabled
- ArgoCD deployed in `argocd` namespace, UI at `localhost:5000`
- Helm repo added: `helm repo add hashicorp https://helm.releases.hashicorp.com`
- The `vault-argocd-all-in-one.yaml` manifest applied

---

## Step 1 — Verify Pods Are Running

```powershell
kubectl get pods -n vault
```

All 3 pods (`vault-local-0`, `vault-local-1`, `vault-local-2`) should show `Running` but `0/1 READY`. This is expected — they're sealed.

If pods are stuck in `Pending`, check for anti-affinity or PVC issues:

```powershell
kubectl describe pod vault-local-1 -n vault
kubectl get pvc -n vault
```

> **Single-node fix:** The Helm chart sets `podAntiAffinity` by default, which prevents multiple pods on one node. Set `affinity: ""` in the Helm values to override this for Docker Desktop.

If PVCs are stuck, force-delete and let ArgoCD recreate:

```powershell
kubectl delete pods --all -n vault --force --grace-period=0
kubectl delete pvc --all -n vault --force --grace-period=0
```

---

## Step 2 — Initialize Vault

Run `operator init` on `vault-local-0`. This generates 5 unseal keys (threshold of 3) and a root token.

```powershell
kubectl exec -n vault vault-local-0 -- vault operator init `
  -key-shares=5 `
  -key-threshold=3 `
  -format=json > vault-keys.json
```

View the output:

```powershell
Get-Content vault-keys.json
```

**⚠ Save `vault-keys.json` immediately. These keys are shown once and cannot be recovered.**

---

## Step 3 — Parse Keys into Variables

```powershell
$keys = (Get-Content vault-keys.json | ConvertFrom-Json)
$u1 = $keys.unseal_keys_b64[0]
$u2 = $keys.unseal_keys_b64[1]
$u3 = $keys.unseal_keys_b64[2]
$rootToken = $keys.root_token
```

---

## Step 4 — Unseal vault-local-0

Unseal requires 3 of the 5 keys (threshold). Run each command and watch `Unseal Progress` increment from `1/3` → `2/3` → `Sealed: false`.

```powershell
kubectl exec -n vault vault-local-0 -- vault operator unseal $u1
kubectl exec -n vault vault-local-0 -- vault operator unseal $u2
kubectl exec -n vault vault-local-0 -- vault operator unseal $u3
```

After the third key, vault-local-0 becomes the **Raft leader**.

---

## Step 5 — Unseal vault-local-1

The `retry_join` stanza in the Raft config automatically joins vault-local-1 to the cluster once vault-local-0 is unsealed. You only need to unseal it.

> **Note:** If you get `Vault is not initialized`, wait 10–15 seconds for the retry_join to complete and try again.

```powershell
kubectl exec -n vault vault-local-1 -- vault operator unseal $u1
kubectl exec -n vault vault-local-1 -- vault operator unseal $u2
kubectl exec -n vault vault-local-1 -- vault operator unseal $u3
```

---

## Step 6 — Unseal vault-local-2

Same process:

```powershell
kubectl exec -n vault vault-local-2 -- vault operator unseal $u1
kubectl exec -n vault vault-local-2 -- vault operator unseal $u2
kubectl exec -n vault vault-local-2 -- vault operator unseal $u3
```

---

## Step 7 — Verify the Raft Cluster

Login with the root token and list peers:

```powershell
kubectl exec -n vault vault-local-0 -- vault login $rootToken
kubectl exec -n vault vault-local-0 -- vault operator raft list-peers
```

Expected output — 3 nodes, 1 leader, 2 followers, all voters:

```
Node             Address                                    State       Voter
----             -------                                    -----       -----
vault-local-0    vault-local-0.vault-local-internal:8201    leader      true
vault-local-1    vault-local-1.vault-local-internal:8201    follower    true
vault-local-2    vault-local-2.vault-local-internal:8201    follower    true
```

---

## Step 8 — Verify All Pods Are Ready

```powershell
kubectl get pods -n vault
```

All 3 should now show `1/1 READY`.

---

## Step 9 — Access the Vault UI

```powershell
kubectl port-forward svc/vault-local-ui -n vault 8200:8200
```

Open http://localhost:8200 and login with the root token.

---

## Step 10 — Verify in ArgoCD

Open http://localhost:5000. The `vault-local` application should show **Synced** and **Healthy**.

---

## Quick Reference

| Item | Value |
|------|-------|
| Vault image | `hashicorp/vault:1.21.2` |
| Helm chart | `hashicorp/vault` v0.32.0 |
| Namespace | `vault` |
| ArgoCD namespace | `argocd` |
| Replicas | 3 (HA Raft) |
| Storage | Integrated Raft |
| Keys file | `vault-keys.json` |

---

## Full Unseal Script (One-Shot)

For convenience, run all unseal steps at once after init:

```powershell
$keys = (Get-Content vault-keys.json | ConvertFrom-Json)
$u1 = $keys.unseal_keys_b64[0]
$u2 = $keys.unseal_keys_b64[1]
$u3 = $keys.unseal_keys_b64[2]
$rootToken = $keys.root_token

foreach ($pod in @("vault-local-0","vault-local-1","vault-local-2")) {
    Write-Host "`nUnsealing $pod..."
    kubectl exec -n vault $pod -- vault operator unseal $u1
    kubectl exec -n vault $pod -- vault operator unseal $u2
    kubectl exec -n vault $pod -- vault operator unseal $u3
}

Write-Host "`nVerifying Raft peers..."
kubectl exec -n vault vault-local-0 -- vault login $rootToken
kubectl exec -n vault vault-local-0 -- vault operator raft list-peers

Write-Host "`nRoot Token: $rootToken"
```
