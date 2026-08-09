# Jobcacher GCS Storage Extension Plugin

Adds **Google Cloud Storage** as a cache backend for the
[Job Cacher](https://plugins.jenkins.io/jobcacher/) plugin, implementing its
`jenkins.plugins.itemstorage.ItemStorage` extension point.

Unlike routing GCS through the S3 extension's interoperability endpoint (which requires
**HMAC** keys), this plugin uses the native GCS client and authenticates with **Application
Default Credentials (ADC)** — so on GKE it works with **Workload Identity** and no keys.

## Why not the S3 extension?

The [S3 storage extension](https://plugins.jenkins.io/s3-jobcacher-storage/) can target
`storage.googleapis.com`, but the AWS SDK path only speaks SigV4/SigV2 with HMAC access
key/secret. There is no way to feed Google OAuth / ADC through it. This plugin talks to GCS
natively instead.

## Authentication (ADC)

Credentials resolve via `GoogleCredentials.getApplicationDefault()`, in order:

1. `GOOGLE_APPLICATION_CREDENTIALS` (service-account JSON file)
2. gcloud user credentials (`gcloud auth application-default login`) — local dev
3. **GKE Workload Identity / metadata server** — recommended for production

No HMAC keys and no service-account JSON are stored in Jenkins configuration.

### Controller ↔ agent

Cache transfers run **on the build agent**. To keep the IAM grant on the controller only, the
plugin mints a short-lived OAuth access token from ADC **on the controller** and ships it to the
agent for the transfer. The token is never written to `config.xml`. If the controller has no ADC,
the agent falls back to resolving ADC itself (e.g. Workload Identity bound to the agent pod).

The shipped token is **downscoped** with a Credential Access Boundary to `roles/storage.objectAdmin`
on the single configured bucket, so a build cannot use it against other buckets the controller
identity can reach.

### Security notes / hardening backlog

- **Cache-key isolation.** Caches for different jobs/branches are separated only by the object-key
  prefix. Attacker-influenceable components (`branch`, cache `path`) are validated to reject empty,
  `.`, `..`, leading/trailing `/`, and control characters, so a build cannot climb out of its
  namespace to poison another job's cache.
- **Known limitation — bucket-scoped token.** The downscoped token is `objectAdmin` on the whole
  bucket. Untrusted build code on the agent could bypass the plugin and use that token directly
  against any object in the bucket. The next hardening step is to add an **object-prefix condition**
  to the Credential Access Boundary so the token only grants access under the current job's prefix.
  That needs integration testing against real GCP (CAB conditions are enforced server-side) and is
  intentionally deferred rather than shipped unverified.

## Configuration

**Manage Jenkins → System → Cache Storage → Google Cloud Storage:**

| Field | Required | Notes |
|-------|----------|-------|
| GCS Bucket Name | yes | Must exist; identity needs `roles/storage.objectAdmin` on it |
| Base Prefix | no | Object-name prefix, e.g. `jenkins/caches/` |
| GCP Project ID | no | Inferred from ADC/metadata when blank |

### Configuration as Code

```yaml
unclassified:
  globalItemStorage:
    storage:
      googleCloudStorage:
        bucketName: "my-jenkins-caches"
        prefix: "jenkins/caches/"
        projectId: "my-gcp-project"
```

## IAM setup (GKE Workload Identity)

Grant to a Google group or the controller's Kubernetes service account — never an individual —
and scope the role to the bucket:

```bash
gcloud storage buckets add-iam-policy-binding gs://my-jenkins-caches \
  --member="principal://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/PROJECT.svc.id.goog/subject/ns/NAMESPACE/sa/KSA_NAME" \
  --role="roles/storage.objectAdmin"
```

## Build

```bash
mvn -ntp clean verify        # unit tests + emulator tests + spotless + CasC compatibility
mvn -ntp hpi:run             # run a local Jenkins with the plugin at http://localhost:8080/jenkins
```

The emulator-backed tests need a Docker daemon. They are skipped automatically when none is
available, so `mvn verify` still passes without one.

## Status

Working, and covered end-to-end. `GCSProfile` is exercised against the
[`fake-gcs-server`](https://github.com/fsouza/fake-gcs-server) emulator over Testcontainers —
resumable upload, existence check, download, rename on job move, and delete on job removal
(including that a sibling job sharing a name prefix is not swept up). Unit tests cover cache-key
validation and JCasC round-tripping.

The GCS client and ADC credentials come from the
[`gcp-java-sdk-storage`](https://plugins.jenkins.io/gcp-java-sdk/) API plugin rather than a direct
`google-cloud-storage` dependency, so this plugin bundles no third-party jars of its own.

Remaining follow-ups:

- Object-prefix condition on the downscoped token's Credential Access Boundary (see
  [Security notes](#security-notes--hardening-backlog)); needs verification against real GCP.
- Optional resumable-download tuning / parallelism for large caches.

## License

MIT
