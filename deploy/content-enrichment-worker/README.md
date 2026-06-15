# Content Enrichment Worker

This worker enriches article bodies from URLs found in MongoDB `newsletter_sources`.
It preserves the raw mail fields and writes derived data into:

```text
newsletter_sources.enrichment.webPage
```

The Spring application reads successful items from that field when generating
Postgres `contents`.

## Key Environment Variables

```text
MONGODB_URI=...
MONGODB_DATABASE=newsletter
POSTGRES_DSN=...
ENRICHMENT_ALLOWED_NEWSLETTER_NAMES=GeekNews,GeekNews Weekly,Maeil Mail
ENRICHMENT_LIMIT=5
ENRICHMENT_INTERVAL_SECONDS=3600
ENRICHMENT_LOOKBACK_DAYS=90
ENRICHMENT_STALE_DAYS=30
ENRICHMENT_MAX_ITEMS_PER_SOURCE=20
ENRICHMENT_CONCURRENCY=1
ENRICHMENT_UPDATE_POSTGRES=false
ENRICHMENT_CREATE_MISSING_POSTGRES_CONTENTS=false
ENRICHMENT_POSTGRES_MAX_EXISTING_CONTENT_LENGTH=700
ENRICHMENT_POSTGRES_PROVIDER_TYPE=BLOG
ENRICHMENT_POSTGRES_PROVIDER_LANGUAGE=en
ENRICHMENT_DRY_RUN=false
ENRICHMENT_RUN_ONCE=true
CONTENT_ENRICHMENT_CRON_SCHEDULE="17 * * * *"
```

`ENRICHMENT_ALLOWED_NEWSLETTER_NAMES` defaults to `GeekNews,GeekNews Weekly,Maeil Mail`.
Maeil Mail is handled with a provider-specific URL rule: only public
`maeil-mail.kr/question/{id}` pages are enriched, while settings, unsubscribe,
private question, wiki, and image links are ignored.

Provider-specific behavior is configured through `ProviderRule` entries in
`content_enrichment_worker.py`. Add a rule there when a newsletter needs custom
source matching, article URL allowlisting, title cleanup, or legacy Postgres row
repair.

When `ENRICHMENT_UPDATE_POSTGRES=true`, the worker also updates matching
Postgres `contents` rows by `newsletter_source_id` and `original_url`, but only
when the current content is shorter than `ENRICHMENT_POSTGRES_MAX_EXISTING_CONTENT_LENGTH`
and the enriched content is longer.

Maeil Mail has an additional repair path for older parser output: when a row
for the same `newsletter_source_id` still points at the Maeil Mail homepage and
contains the mail template text, the worker replaces it with the public
`question/{id}` URL and enriched answer body.

When `ENRICHMENT_CREATE_MISSING_POSTGRES_CONTENTS=true`, the worker creates
missing Postgres `contents` rows from successful enrichment items when neither
the `newsletter_source_id` nor `original_url` already exists.

## Production Run

Production does not keep this worker running as a resident container. The
deploy script first registers `deploy/run-content-enrichment-worker.sh` in the
server crontab. If `crontab` is unavailable, it falls back to a systemd timer
named `newsletter-content-enrichment-worker.timer`. Both paths execute:

```bash
docker compose --profile batch run --rm --no-deps content-enrichment-worker
```

The default schedule is hourly at minute 17. Override it with
`CONTENT_ENRICHMENT_CRON_SCHEDULE` in the deploy `.env` file.
The runner uses `deploy/logs/content-enrichment-worker/worker.lock` by default
to prevent overlapping runs. Override it with `CONTENT_ENRICHMENT_LOCK_FILE` only
when the target path is writable by the deploy user.

## Local One-Off Run

```bash
pip install -r deploy/content-enrichment-worker/requirements.txt
MONGODB_URI='mongodb://user:password@host:27017/newsletter?authSource=newsletter' \
POSTGRES_DSN='postgresql://user:password@host:5432/newsletter' \
ENRICHMENT_DRY_RUN=true \
ENRICHMENT_UPDATE_POSTGRES=false \
ENRICHMENT_CREATE_MISSING_POSTGRES_CONTENTS=false \
ENRICHMENT_RUN_ONCE=true \
python deploy/content-enrichment-worker/content_enrichment_worker.py
```
