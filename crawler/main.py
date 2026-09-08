import logging
import re
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel
import trafilatura
import httpx

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger("crawler")

app = FastAPI(title="Quick News Article Crawler", version="1.0.0")

class ArticleExtractResponse(BaseModel):
    url: str
    success: bool
    title: str | None = None
    content: str | None = None
    image_url: str | None = None
    length: int = 0
    error: str | None = None

def extract_meta_image(html: str) -> str | None:
    patterns = [
        r'<meta[^>]+property=["\']og:image["\'][^>]+content=["\']([^"\']+)["\']',
        r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+property=["\']og:image["\']',
        r'<meta[^>]+name=["\']twitter:image(?::src)?["\'][^>]+content=["\']([^"\']+)["\']',
        r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+name=["\']twitter:image(?::src)?["\']',
    ]
    for pat in patterns:
        m = re.search(pat, html, re.IGNORECASE)
        if m:
            val = m.group(1).strip()
            if val.startswith("http://") or val.startswith("https://"):
                return val
    return None

def extract_meta_title(html: str) -> str | None:
    patterns = [
        r'<meta[^>]+property=["\']og:title["\'][^>]+content=["\']([^"\']+)["\']',
        r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+property=["\']og:title["\']',
        r'<title[^>]*>([^<]+)</title>',
    ]
    for pat in patterns:
        m = re.search(pat, html, re.IGNORECASE)
        if m:
            val = m.group(1).strip()
            if val:
                return val
    return None

@app.get("/health")
def health_check():
    return {"status": "ok"}

@app.post("/api/v1/extract", response_model=ArticleExtractResponse)
async def extract_article(url: str = Query(..., description="Target URL to extract article text from")):
    logger.info(f"Received extraction request for URL: {url}")
    try:
        resp = None
        extracted_text = None
        extracted_title = None
        extracted_image = None
        status_code = 0
        try:
            async with httpx.AsyncClient(timeout=15.0, follow_redirects=True, headers={
                "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7"
            }) as client:
                resp = await client.get(url)
                status_code = resp.status_code

            if resp and resp.status_code == 200:
                html_body = resp.text
                extracted_image = extract_meta_image(html_body)
                extracted_title = extract_meta_title(html_body)
                extracted_text = trafilatura.extract(
                    html_body,
                    include_links=False,
                    include_images=False,
                    include_formatting=False,
                    no_fallback=False
                )
        except Exception as de:
            logger.info(f"Direct request failed for {url}: {de}. Will attempt Jina fallback.")

        # Fallback to Jina reader proxy if direct extraction failed, blocked (e.g. 403), or yielded very short content (<300 chars)
        if not extracted_text or len(extracted_text.strip()) < 300:
            logger.info(f"Direct extraction insufficient (status={status_code}, len={len(extracted_text) if extracted_text else 0}). Trying Jina reader fallback for: {url}")
            try:
                async with httpx.AsyncClient(timeout=25.0, follow_redirects=True) as jina_client:
                    jina_resp = await jina_client.get(
                        f"https://r.jina.ai/{url}",
                        headers={
                            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
                            "Accept": "text/plain, text/markdown"
                        }
                    )
                    if jina_resp.status_code == 200 and len(jina_resp.text.strip()) >= 300:
                        jina_content = jina_resp.text.strip()
                        logger.info(f"Jina reader successfully extracted {len(jina_content)} chars from URL: {url}")
                        if not extracted_image:
                            img_match = re.search(r'!\[.*?\]\((https?://[^\s\)]+)\)', jina_content)
                            if img_match:
                                extracted_image = img_match.group(1).strip()

                        return ArticleExtractResponse(
                            url=url,
                            success=True,
                            title=extracted_title,
                            content=jina_content,
                            image_url=extracted_image,
                            length=len(jina_content)
                        )
            except Exception as je:
                logger.warning(f"Jina reader fallback failed for {url}: {je}")

        if not extracted_text or len(extracted_text.strip()) < 100:
            logger.warning(f"Trafilatura and fallback could not extract text for URL: {url}")
            return ArticleExtractResponse(
                url=url,
                success=False,
                error=f"Extraction failed (status={status_code}, no body found)"
            )

        logger.info(f"Successfully extracted {len(extracted_text)} chars from URL: {url}")
        return ArticleExtractResponse(
            url=url,
            success=True,
            title=extracted_title,
            content=extracted_text.strip(),
            image_url=extracted_image,
            length=len(extracted_text.strip())
        )

    except Exception as e:
        logger.error(f"Error extracting URL {url}: {str(e)}", exc_info=True)
        return ArticleExtractResponse(
            url=url,
            success=False,
            error=str(e)
        )
