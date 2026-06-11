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
ENRICHMENT_ALLOWED_NEWSLETTER_NAMES=GeekNews,GeekNews Weekly
ENRICHMENT_LIMIT=5
ENRICHMENT_INTERVAL_SECONDS=3600
ENRICHMENT_LOOKBACK_DAYS=90
ENRICHMENT_STALE_DAYS=30
ENRICHMENT_MAX_ITEMS_PER_SOURCE=20
ENRICHMENT_CONCURRENCY=1
ENRICHMENT_DRY_RUN=false
ENRICHMENT_RUN_ONCE=true
CONTENT_ENRICHMENT_CRON_SCHEDULE="17 * * * *"
```

`ENRICHMENT_ALLOWED_NEWSLETTER_NAMES` defaults to `GeekNews,GeekNews Weekly`
because `news.hada.io` has the most reliable URL structure among the currently
reviewed publishers.

## Production Run

Production does not keep this worker running as a resident container. The
server crontab runs `deploy/run-content-enrichment-worker.sh`, which executes:

```bash
docker compose --profile batch run --rm --no-deps content-enrichment-worker
```

The default schedule is hourly at minute 17. Override it with
`CONTENT_ENRICHMENT_CRON_SCHEDULE` in the deploy `.env` file.

## Local One-Off Run

```bash
pip install -r deploy/content-enrichment-worker/requirements.txt
MONGODB_URI='mongodb://user:password@host:27017/newsletter?authSource=newsletter' \
ENRICHMENT_DRY_RUN=true \
ENRICHMENT_RUN_ONCE=true \
python deploy/content-enrichment-worker/content_enrichment_worker.py
```
