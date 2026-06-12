import asyncio
import hashlib
import logging
import os
import re
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any
from urllib.parse import parse_qsl, urlencode, urljoin, urlparse, urlunparse

import aiohttp
import asyncpg
import trafilatura
from bs4 import BeautifulSoup
from motor.motor_asyncio import AsyncIOMotorClient


LOGGER = logging.getLogger("content-enrichment-worker")
USER_AGENT = "Mozilla/5.0 compatible; QuickNewsContentEnrichmentWorker/1.0"
VERSION = 1

NON_ARTICLE_HOSTS = {
    "apps.apple.com",
    "github.com",
    "linkedin.com",
    "reddit.com",
    "twitter.com",
    "x.com",
    "youtu.be",
    "youtube.com",
}
NON_ARTICLE_PATH_KEYWORDS = {
    "preference",
    "preferences",
    "privacy",
    "setting",
    "unsubscribe",
}
NON_HTML_EXTENSIONS = {
    ".gif",
    ".gz",
    ".jpeg",
    ".jpg",
    ".mov",
    ".mp4",
    ".pdf",
    ".png",
    ".svg",
    ".tar",
    ".tgz",
    ".webp",
    ".zip",
}
TRACKING_QUERY_KEYS = {
    "fbclid",
    "gclid",
    "mc_cid",
    "mc_eid",
}
TITLE_STOP_WORDS = {
    "about",
    "after",
    "into",
    "that",
    "this",
    "what",
    "when",
    "where",
    "which",
    "with",
    "your",
}
GENERAL_PROVIDER_HINTS = (
    ("news@hada.io", "GeekNews"),
    ("news@hada.io", "GeekNews Weekly"),
    ("news.hada.io", "GeekNews"),
    ("news.hada.io", "GeekNews Weekly"),
    ("awesome ios weekly", "Awesome iOS Weekly"),
    ("jacobbartlett@substack.com", "Jacob's Tech Tavern"),
    ("jacob's tech tavern", "Jacob's Tech Tavern"),
    ("fatbobman@substack.com", "Fatbobman's Swift Weekly"),
    ("fatbobman's swift weekly", "Fatbobman's Swift Weekly"),
)
URL_REGEX = re.compile(r"https?://[^\s<>()\"']+")
WHITESPACE_REGEX = re.compile(r"\s+")
TITLE_SEPARATOR_REGEX = re.compile(r"[^\w]+", re.UNICODE)
MAEIL_MAIL_QUESTION_PATH_REGEX = re.compile(r"^/question/\d+/?$")
MAEIL_MAIL_SUBJECT_PREFIX_REGEX = re.compile(r"^\s*\[매일메일]\s*")


@dataclass(frozen=True)
class Settings:
    mongodb_uri: str
    database_name: str
    collection_name: str
    allowed_newsletter_names: set[str]
    limit: int
    interval_seconds: int
    lookback_days: int
    stale_days: int
    max_items_per_source: int
    max_content_length: int
    min_content_length: int
    concurrency: int
    request_timeout_seconds: int
    dry_run: bool
    run_once: bool
    postgres_dsn: str | None
    update_postgres: bool
    create_missing_postgres_contents: bool
    postgres_max_existing_content_length: int
    postgres_provider_type: str
    postgres_provider_language: str

    @classmethod
    def from_env(cls) -> "Settings":
        mongodb_uri = required_env("MONGODB_URI")
        database_name = os.getenv("MONGODB_DATABASE") or database_from_uri(mongodb_uri) or "newsletter"
        allowed_names = string_set(
            os.getenv("ENRICHMENT_ALLOWED_NEWSLETTER_NAMES", "GeekNews,GeekNews Weekly,Maeil Mail")
        )
        if not allowed_names:
            raise ValueError("ENRICHMENT_ALLOWED_NEWSLETTER_NAMES must not be empty")

        return cls(
            mongodb_uri=mongodb_uri,
            database_name=database_name,
            collection_name=os.getenv("ENRICHMENT_SOURCE_COLLECTION", "newsletter_sources"),
            allowed_newsletter_names=allowed_names,
            limit=int(os.getenv("ENRICHMENT_LIMIT", "20")),
            interval_seconds=int(os.getenv("ENRICHMENT_INTERVAL_SECONDS", "3600")),
            lookback_days=int(os.getenv("ENRICHMENT_LOOKBACK_DAYS", "90")),
            stale_days=int(os.getenv("ENRICHMENT_STALE_DAYS", "30")),
            max_items_per_source=int(os.getenv("ENRICHMENT_MAX_ITEMS_PER_SOURCE", "20")),
            max_content_length=int(os.getenv("ENRICHMENT_MAX_CONTENT_LENGTH", "9000")),
            min_content_length=int(os.getenv("ENRICHMENT_MIN_CONTENT_LENGTH", "700")),
            concurrency=int(os.getenv("ENRICHMENT_CONCURRENCY", "4")),
            request_timeout_seconds=int(os.getenv("ENRICHMENT_REQUEST_TIMEOUT_SECONDS", "8")),
            dry_run=boolean_env("ENRICHMENT_DRY_RUN", False),
            run_once=boolean_env("ENRICHMENT_RUN_ONCE", False),
            postgres_dsn=os.getenv("POSTGRES_DSN"),
            update_postgres=boolean_env("ENRICHMENT_UPDATE_POSTGRES", False),
            create_missing_postgres_contents=boolean_env("ENRICHMENT_CREATE_MISSING_POSTGRES_CONTENTS", False),
            postgres_max_existing_content_length=int(os.getenv("ENRICHMENT_POSTGRES_MAX_EXISTING_CONTENT_LENGTH", "700")),
            postgres_provider_type=os.getenv("ENRICHMENT_POSTGRES_PROVIDER_TYPE", "BLOG"),
            postgres_provider_language=os.getenv("ENRICHMENT_POSTGRES_PROVIDER_LANGUAGE", "en"),
        )


@dataclass(frozen=True)
class LinkCandidate:
    url: str
    normalized_url: str
    title: str | None


@dataclass(frozen=True)
class ProviderRule:
    name: str
    hints: tuple[str, ...] = ()
    allowed_hosts: frozenset[str] = frozenset()
    allowed_path_regex: re.Pattern[str] | None = None
    use_source_subject_for_links: bool = False
    subject_prefix_regex: re.Pattern[str] | None = None
    repair_original_urls: tuple[str, ...] = ()
    repair_content_markers: tuple[str, ...] = ()

    def matches_source(self, source: dict[str, Any]) -> bool:
        headers = source.get("headers") or {}
        haystacks = [
            normalize_text(source.get("sender") or ""),
            normalize_text(source.get("senderEmail") or ""),
            normalize_text(source.get("subject") or ""),
            normalize_text(headers.get("RSS-Feed-URL") or ""),
            normalize_text(headers.get("RSS-Item-URL") or ""),
        ]
        return any(
            hint.lower() in haystack.lower()
            for hint in self.hints
            for haystack in haystacks
        )

    def accepts_url(self, normalized_url: str) -> bool:
        if not self.allowed_hosts and self.allowed_path_regex is None:
            return True

        parsed = urlparse(normalized_url)
        host = parsed.netloc.lower()
        if self.allowed_hosts and host not in self.allowed_hosts:
            return False
        return self.allowed_path_regex is None or bool(self.allowed_path_regex.match(parsed.path))

    def candidate_title(
        self,
        source: dict[str, Any],
        url: str,
        title: str | None,
    ) -> str | None:
        if not self.accepts_url(normalize_url(url)):
            return title

        raw_title = title or ""
        if self.use_source_subject_for_links:
            raw_title = source.get("subject") or raw_title
        if self.subject_prefix_regex:
            raw_title = self.subject_prefix_regex.sub("", raw_title)
        return normalize_text(raw_title) or None

    @property
    def has_postgres_repair(self) -> bool:
        return bool(self.repair_original_urls or self.repair_content_markers)

    @property
    def repair_content_patterns(self) -> list[str]:
        return [f"%{marker}%" for marker in self.repair_content_markers]


PROVIDER_RULES = {
    "Maeil Mail": ProviderRule(
        name="Maeil Mail",
        hints=("noreply@maeil-mail.kr", "maeil-mail", "[매일메일]"),
        allowed_hosts=frozenset({"maeil-mail.kr", "www.maeil-mail.kr"}),
        allowed_path_regex=MAEIL_MAIL_QUESTION_PATH_REGEX,
        use_source_subject_for_links=True,
        subject_prefix_regex=MAEIL_MAIL_SUBJECT_PREFIX_REGEX,
        repair_original_urls=("https://www.maeil-mail.kr", "https://www.maeil-mail.kr/"),
        repair_content_markers=("오늘의 질문이 도착했습니다",),
    ),
}

PROVIDER_HINTS = (
    GENERAL_PROVIDER_HINTS
    + tuple(
        (hint, rule.name)
        for rule in PROVIDER_RULES.values()
        for hint in rule.hints
    )
)


async def main() -> None:
    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )
    settings = Settings.from_env()
    client = AsyncIOMotorClient(settings.mongodb_uri)
    collection = client[settings.database_name][settings.collection_name]
    postgres_pool = await create_postgres_pool(settings)

    LOGGER.info(
        (
            "Starting content enrichment worker. database=%s collection=%s allowed=%s "
            "dryRun=%s updatePostgres=%s createMissingPostgresContents=%s"
        ),
        settings.database_name,
        settings.collection_name,
        sorted(settings.allowed_newsletter_names),
        settings.dry_run,
        settings.update_postgres,
        settings.create_missing_postgres_contents,
    )

    try:
        while True:
            await run_once(collection, settings, postgres_pool)
            if settings.run_once:
                break
            await asyncio.sleep(settings.interval_seconds)
    finally:
        if postgres_pool:
            await postgres_pool.close()
        client.close()


async def create_postgres_pool(settings: Settings) -> asyncpg.Pool | None:
    if not settings.update_postgres and not settings.create_missing_postgres_contents:
        return None
    if not settings.postgres_dsn:
        raise ValueError(
            "POSTGRES_DSN is required when ENRICHMENT_UPDATE_POSTGRES=true "
            "or ENRICHMENT_CREATE_MISSING_POSTGRES_CONTENTS=true"
        )
    return await asyncpg.create_pool(dsn=settings.postgres_dsn, min_size=1, max_size=1)


async def run_once(collection: Any, settings: Settings, postgres_pool: asyncpg.Pool | None) -> None:
    candidates = await find_candidate_sources(collection, settings)
    LOGGER.info("Found %s source candidates", len(candidates))
    if not candidates:
        return

    timeout = aiohttp.ClientTimeout(total=settings.request_timeout_seconds)
    connector = aiohttp.TCPConnector(limit=settings.concurrency)
    async with aiohttp.ClientSession(timeout=timeout, connector=connector, headers={"User-Agent": USER_AGENT}) as session:
        for source in candidates:
            await enrich_source(collection, session, source, settings, postgres_pool)


async def find_candidate_sources(collection: Any, settings: Settings) -> list[dict[str, Any]]:
    now = datetime.now(timezone.utc)
    cutoff = now - timedelta(days=settings.lookback_days)
    stale_cutoff = now - timedelta(days=settings.stale_days)
    provider_conditions = provider_query(settings.allowed_newsletter_names)

    query: dict[str, Any] = {
        "receivedDate": {"$gte": cutoff.replace(tzinfo=None)},
        "$and": [
            {"$or": provider_conditions},
            {
                "$or": [
                    {"enrichment.webPage.processedAt": {"$exists": False}},
                    {"enrichment.webPage.processedAt": {"$lt": stale_cutoff.replace(tzinfo=None)}},
                    {"enrichment.webPage.status": {"$in": ["partial", "failed"]}},
                ]
            },
        ],
    }

    cursor = collection.find(query).sort("receivedDate", -1).limit(settings.limit)
    return await cursor.to_list(length=settings.limit)


def provider_query(allowed_newsletter_names: set[str]) -> list[dict[str, Any]]:
    conditions: list[dict[str, Any]] = []
    for provider_name in allowed_newsletter_names:
        regex = {"$regex": re.escape(provider_name), "$options": "i"}
        conditions.extend(
            [
                {"senderEmail": regex},
                {"sender": regex},
                {"headers.RSS-Feed-URL": regex},
                {"headers.RSS-Item-URL": regex},
            ]
        )

    for hint, provider_name in PROVIDER_HINTS:
        if provider_name not in allowed_newsletter_names:
            continue
        regex = {"$regex": re.escape(hint), "$options": "i"}
        conditions.extend(
            [
                {"senderEmail": regex},
                {"sender": regex},
                {"subject": regex},
                {"headers.RSS-Feed-URL": regex},
                {"headers.RSS-Item-URL": regex},
            ]
        )
    return conditions or [{"_id": {"$exists": False}}]


async def enrich_source(
    collection: Any,
    session: aiohttp.ClientSession,
    source: dict[str, Any],
    settings: Settings,
    postgres_pool: asyncpg.Pool | None,
) -> None:
    source_id = source.get("_id")
    existing_items = source.get("enrichment", {}).get("webPage", {}).get("items", [])
    existing_by_url = {
        item.get("normalizedUrl") or normalize_url(item.get("url", "")): item
        for item in existing_items
        if item.get("url") or item.get("normalizedUrl")
    }

    candidates = [
        candidate
        for candidate in extract_link_candidates(source)
        if candidate.normalized_url not in existing_by_url
    ][: settings.max_items_per_source]

    if not candidates:
        await update_source(collection, source, existing_items, aggregate_status(existing_items), settings, "no-new-url-candidates")
        updated_contents, created_contents, linked_contents = await sync_postgres_contents(postgres_pool, source, existing_items, settings)
        LOGGER.info(
            (
                "No new URL candidates. sourceId=%s postgresUpdated=%s "
                "postgresCreated=%s postgresLinked=%s"
            ),
            source_id,
            updated_contents,
            created_contents,
            linked_contents,
        )
        return

    semaphore = asyncio.Semaphore(settings.concurrency)
    tasks = [enrich_link(session, semaphore, candidate, settings) for candidate in candidates]
    new_items = await asyncio.gather(*tasks)
    merged_items = existing_items + new_items
    status = aggregate_status(merged_items)

    await update_source(collection, source, merged_items, status, settings)
    updated_contents, created_contents, linked_contents = await sync_postgres_contents(postgres_pool, source, merged_items, settings)
    LOGGER.info(
        (
            "Processed source. sourceId=%s candidates=%s success=%s skipped=%s failed=%s "
            "postgresUpdated=%s postgresCreated=%s postgresLinked=%s"
        ),
        source_id,
        len(candidates),
        sum(1 for item in new_items if item["status"] == "success"),
        sum(1 for item in new_items if item["status"] == "skipped"),
        sum(1 for item in new_items if item["status"] == "failed"),
        updated_contents,
        created_contents,
        linked_contents,
    )


def extract_link_candidates(source: dict[str, Any]) -> list[LinkCandidate]:
    html = source.get("htmlContent") or ""
    text = source.get("content") or ""
    headers = source.get("headers") or {}
    provider_rule = source_provider_rule(source)
    links: list[LinkCandidate] = []

    append_candidate(
        links,
        clean_url(headers.get("RSS-Item-URL", "")),
        source.get("subject"),
        provider_rule,
    )

    if html:
        soup = BeautifulSoup(html, "lxml")
        for anchor in soup.select("a[href]"):
            url = clean_url(anchor.get("href", ""))
            title = provider_rule.candidate_title(
                source,
                url,
                normalize_text(anchor.get_text(" ", strip=True)) or None,
            )
            append_candidate(links, url, title, provider_rule)

    for match in URL_REGEX.finditer(text):
        url = clean_url(match.group(0))
        append_candidate(links, url, provider_rule.candidate_title(source, url, None), provider_rule)

    deduped: dict[str, LinkCandidate] = {}
    for link in links:
        deduped.setdefault(link.normalized_url, link)
    return list(deduped.values())


async def sync_postgres_contents(
    postgres_pool: asyncpg.Pool | None,
    source: dict[str, Any],
    items: list[dict[str, Any]],
    settings: Settings,
) -> tuple[int, int, int]:
    if not postgres_pool:
        return 0, 0, 0

    source_id = str(source.get("_id", ""))
    if not source_id:
        return 0, 0, 0

    if settings.dry_run:
        LOGGER.info("Dry-run postgres update skipped. sourceId=%s items=%s", source_id, len(items))
        return 0, 0, 0

    provider_name = source_provider_name(source)
    provider_rule = provider_rule_by_name(provider_name)
    updated_count = 0
    created_count = 0
    linked_count = 0
    async with postgres_pool.acquire() as connection:
        provider_id = None
        if settings.create_missing_postgres_contents:
            provider_id = await resolve_content_provider_id(connection, source, settings)

        for item in items:
            content = item.get("content")
            url = item.get("url")
            if item.get("status") != "success" or not content or not url:
                continue

            result = await connection.execute(
                """
                UPDATE contents
                SET
                    content = $1,
                    image_url = COALESCE(NULLIF(image_url, ''), $2),
                    original_url = CASE
                        WHEN $6 THEN $4
                        ELSE original_url
                    END,
                    updated_at = NOW()
                WHERE newsletter_source_id = $3
                  AND (
                      original_url = $4
                      OR (
                          $6
                          AND original_url = ANY($7::text[])
                      )
                  )
                  AND (
                      length(content) <= $5
                      OR (
                          $6
                          AND content LIKE ANY($8::text[])
                      )
                  )
                  AND (
                      length(content) < length($1)
                      OR (
                          $6
                          AND content LIKE ANY($8::text[])
                      )
                  )
                """,
                content,
                item.get("imageUrl"),
                source_id,
                url,
                settings.postgres_max_existing_content_length,
                provider_rule.has_postgres_repair,
                list(provider_rule.repair_original_urls),
                provider_rule.repair_content_patterns,
            )
            updated_count += int(result.rsplit(" ", 1)[-1])

            if settings.create_missing_postgres_contents and provider_id is not None:
                insert_result = await insert_missing_postgres_content(connection, source, item, content, provider_id)
                created_count += insert_result
                linked_count += await link_postgres_content_provider(connection, source, item, provider_id)

    return updated_count, created_count, linked_count


async def resolve_content_provider_id(
    connection: asyncpg.Connection,
    source: dict[str, Any],
    settings: Settings,
) -> int | None:
    provider_name = source_provider_name(source)
    if not provider_name:
        return None

    existing_id = await connection.fetchval(
        """
        SELECT id
        FROM content_provider
        WHERE name = $1
        ORDER BY id ASC
        LIMIT 1
        """,
        provider_name,
    )
    if existing_id is not None:
        return int(existing_id)

    headers = source.get("headers") or {}
    channel = headers.get("RSS-Feed-URL") or source.get("senderEmail") or provider_name
    provider_id = await connection.fetchval(
        """
        INSERT INTO content_provider (name, channel, language, type, created_at, updated_at)
        VALUES ($1, $2, $3, $4, NOW(), NOW())
        RETURNING id
        """,
        provider_name,
        channel,
        settings.postgres_provider_language,
        settings.postgres_provider_type,
    )
    return int(provider_id) if provider_id is not None else None


async def insert_missing_postgres_content(
    connection: asyncpg.Connection,
    source: dict[str, Any],
    item: dict[str, Any],
    content: str,
    provider_id: int,
) -> int:
    source_id = str(source.get("_id", ""))
    original_url = item.get("url")
    if not source_id or not original_url:
        return 0

    provider_name = source_provider_name(source)
    if not provider_name:
        return 0

    published_at = (source.get("receivedDate") or datetime.now(timezone.utc).replace(tzinfo=None)).date()
    title = (source.get("subject") or item.get("title") or original_url).strip()
    result = await connection.execute(
        """
        INSERT INTO contents (
            newsletter_source_id,
            title,
            content,
            newsletter_name,
            original_url,
            image_url,
            published_at,
            content_provider_id,
            created_at,
            updated_at
        )
        SELECT $1::varchar, $2::text, $3::text, $4::text, $5::text, $6::text, $7::date, $8::bigint, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1
            FROM contents
            WHERE newsletter_source_id = $1::varchar
               OR original_url = $5::text
        )
        """,
        source_id,
        title,
        content,
        provider_name,
        original_url,
        item.get("imageUrl"),
        published_at,
        provider_id,
    )
    return int(result.rsplit(" ", 1)[-1])


async def link_postgres_content_provider(
    connection: asyncpg.Connection,
    source: dict[str, Any],
    item: dict[str, Any],
    provider_id: int,
) -> int:
    source_id = str(source.get("_id", ""))
    original_url = item.get("url")
    if not source_id or not original_url:
        return 0

    result = await connection.execute(
        """
        UPDATE contents
        SET
            content_provider_id = $1,
            updated_at = NOW()
        WHERE content_provider_id IS NULL
          AND (
              newsletter_source_id = $2::varchar
              OR original_url = $3::text
          )
        """,
        provider_id,
        source_id,
        original_url,
    )
    return int(result.rsplit(" ", 1)[-1])


def source_provider_name(source: dict[str, Any]) -> str:
    rule = source_provider_rule(source)
    if rule.name:
        return rule.name
    return normalize_text(source.get("sender") or source.get("senderEmail") or "")


def source_provider_rule(source: dict[str, Any]) -> ProviderRule:
    return next(
        (rule for rule in PROVIDER_RULES.values() if rule.matches_source(source)),
        default_provider_rule(),
    )


def provider_rule_by_name(provider_name: str) -> ProviderRule:
    return PROVIDER_RULES.get(provider_name, default_provider_rule())


def default_provider_rule() -> ProviderRule:
    return ProviderRule(name="")


def append_candidate(
    links: list[LinkCandidate],
    url: str,
    title: str | None,
    provider_rule: ProviderRule,
) -> None:
    normalized_url = normalize_url(url)
    if (
        not normalized_url
        or is_non_article_url(normalized_url)
        or not provider_rule.accepts_url(normalized_url)
    ):
        return
    links.append(LinkCandidate(url=url, normalized_url=normalized_url, title=title))


async def enrich_link(
    session: aiohttp.ClientSession,
    semaphore: asyncio.Semaphore,
    candidate: LinkCandidate,
    settings: Settings,
) -> dict[str, Any]:
    async with semaphore:
        fetched_at = datetime.now(timezone.utc).replace(tzinfo=None)
        try:
            async with session.get(candidate.url, allow_redirects=True) as response:
                content_type = response.headers.get("content-type", "").lower()
                if response.status >= 400:
                    return skipped_item(candidate, fetched_at, f"http-{response.status}")
                if "text/html" not in content_type and "application/xhtml" not in content_type:
                    return skipped_item(candidate, fetched_at, "non-html-content")

                html = await response.text(errors="ignore")
                final_url = str(response.url)
                content = extract_content(html, final_url)
                if not content or len(content) < settings.min_content_length:
                    return skipped_item(candidate, fetched_at, "content-too-short")
                if candidate.title and not relevant_to_title(candidate.title, content):
                    return skipped_item(candidate, fetched_at, "title-mismatch")

                content = limit_length(content, settings.max_content_length)
                return {
                    "url": candidate.url,
                    "normalizedUrl": candidate.normalized_url,
                    "title": candidate.title,
                    "content": content,
                    "imageUrl": extract_image_url(html, final_url),
                    "status": "success",
                    "reason": None,
                    "fetchedAt": fetched_at,
                    "contentHash": hashlib.sha256(content.encode("utf-8")).hexdigest(),
                }
        except asyncio.TimeoutError:
            return failed_item(candidate, fetched_at, "timeout")
        except Exception as exc:
            LOGGER.debug("Failed to enrich url=%s: %s", candidate.url, exc, exc_info=True)
            return failed_item(candidate, fetched_at, exc.__class__.__name__)


def extract_content(html: str, url: str) -> str | None:
    extracted = trafilatura.extract(
        html,
        url=url,
        include_comments=False,
        include_tables=False,
        favor_precision=True,
    )
    if extracted:
        return normalize_multiline_text(extracted)

    soup = BeautifulSoup(html, "lxml")
    for selector in ("script", "style", "noscript", "svg", "canvas", "iframe", "nav", "footer", "header", "aside", "form"):
        for node in soup.select(selector):
            node.decompose()
    blocks = [
        normalize_text(node.get_text(" ", strip=True))
        for node in soup.select("article p, main p, .post-content p, .entry-content p, p")
    ]
    blocks = [block for block in blocks if len(block) >= 24]
    if not blocks:
        return None
    return normalize_multiline_text("\n\n".join(dict.fromkeys(blocks)))


def extract_image_url(html: str, base_url: str) -> str | None:
    soup = BeautifulSoup(html, "lxml")
    selectors = (
        ("meta[property='og:image']", "content"),
        ("meta[name='twitter:image']", "content"),
        ("meta[property='twitter:image']", "content"),
        ("img[src]", "src"),
    )
    for selector, attr in selectors:
        value = soup.select_one(selector)
        raw_url = value.get(attr) if value else None
        if raw_url:
            return urljoin(base_url, raw_url.strip())
    return None


def relevant_to_title(title: str, content: str) -> bool:
    terms = significant_terms(title)
    if not terms:
        return True
    haystack = content.lower()
    matches = [term for term in terms if term in haystack]
    return len(matches) >= min(2, len(terms)) or any(any(char.isdigit() for char in term) for term in matches)


def significant_terms(title: str) -> list[str]:
    return list(
        dict.fromkeys(
            term
            for term in TITLE_SEPARATOR_REGEX.sub(" ", title.lower()).split()
            if len(term) >= 4 and term not in TITLE_STOP_WORDS
        )
    )


async def update_source(
    collection: Any,
    source: dict[str, Any],
    items: list[dict[str, Any]],
    status: str,
    settings: Settings,
    reason: str | None = None,
) -> None:
    web_page = {
        "version": VERSION,
        "status": status,
        "reason": reason,
        "processedAt": datetime.now(timezone.utc).replace(tzinfo=None),
        "items": items,
    }
    if settings.dry_run:
        LOGGER.info("Dry-run update skipped. sourceId=%s status=%s items=%s", source.get("_id"), status, len(items))
        return
    await collection.update_one({"_id": source["_id"]}, {"$set": {"enrichment.webPage": web_page}})


def aggregate_status(items: list[dict[str, Any]]) -> str:
    if any(item.get("status") == "success" for item in items):
        return "success"
    if any(item.get("status") == "failed" for item in items):
        return "failed"
    if not items:
        return "skipped"
    return "skipped"


def skipped_item(candidate: LinkCandidate, fetched_at: datetime, reason: str) -> dict[str, Any]:
    return result_item(candidate, fetched_at, "skipped", reason)


def failed_item(candidate: LinkCandidate, fetched_at: datetime, reason: str) -> dict[str, Any]:
    return result_item(candidate, fetched_at, "failed", reason)


def result_item(candidate: LinkCandidate, fetched_at: datetime, status: str, reason: str) -> dict[str, Any]:
    return {
        "url": candidate.url,
        "normalizedUrl": candidate.normalized_url,
        "title": candidate.title,
        "content": None,
        "imageUrl": None,
        "status": status,
        "reason": reason,
        "fetchedAt": fetched_at,
        "contentHash": None,
    }


def normalize_url(url: str) -> str:
    parsed = urlparse(clean_url(url))
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        return ""
    query = [
        (key, value)
        for key, value in parse_qsl(parsed.query, keep_blank_values=True)
        if not key.lower().startswith("utm_") and key.lower() not in TRACKING_QUERY_KEYS
    ]
    path = parsed.path.rstrip("/") or "/"
    return urlunparse(
        (
            parsed.scheme.lower(),
            parsed.netloc.lower(),
            path,
            "",
            urlencode(sorted(query), doseq=True),
            "",
        )
    )


def clean_url(url: str) -> str:
    return url.strip().strip(".,;:!?)>]}'\"")


def is_non_article_url(url: str) -> bool:
    parsed = urlparse(url)
    host = parsed.netloc.lower()
    path = parsed.path.lower()
    path_parts = {part for part in path.split("/") if part}
    return (
        any(host == blocked or host.endswith(f".{blocked}") for blocked in NON_ARTICLE_HOSTS)
        or any(keyword in path_parts for keyword in NON_ARTICLE_PATH_KEYWORDS)
        or any(path.endswith(extension) for extension in NON_HTML_EXTENSIONS)
    )


def normalize_text(value: str) -> str:
    return WHITESPACE_REGEX.sub(" ", value.replace("\xa0", " ")).strip()


def normalize_multiline_text(value: str) -> str:
    lines = [normalize_text(line) for line in value.splitlines()]
    return "\n".join(line for line in lines if line)


def limit_length(content: str, max_length: int) -> str:
    if len(content) <= max_length:
        return content
    return content[:max_length].rsplit("\n", 1)[0].strip() + "..."


def database_from_uri(uri: str) -> str | None:
    path = urlparse(uri).path.strip("/")
    return path or None


def string_set(value: str) -> set[str]:
    return {item.strip() for item in value.split(",") if item.strip()}


def boolean_env(key: str, default: bool) -> bool:
    value = os.getenv(key)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def required_env(key: str) -> str:
    value = os.getenv(key)
    if not value:
        raise ValueError(f"{key} is required")
    return value


if __name__ == "__main__":
    asyncio.run(main())
