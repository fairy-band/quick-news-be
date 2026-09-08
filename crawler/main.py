import logging
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
    length: int = 0
    error: str | None = None

@app.get("/health")
def health_check():
    return {"status": "ok"}

@app.post("/api/v1/extract", response_model=ArticleExtractResponse)
async def extract_article(url: str = Query(..., description="Target URL to extract article text from")):
    logger.info(f"Received extraction request for URL: {url}")
    try:
        async with httpx.AsyncClient(timeout=15.0, follow_redirects=True, headers={
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7"
        }) as client:
            resp = await client.get(url)

        extracted_text = None
        if resp.status_code == 200:
            extracted_text = trafilatura.extract(
                resp.text,
                include_links=False,
                include_images=False,
                include_formatting=False,
                no_fallback=False
            )

        # Fallback to Jina reader proxy if direct extraction failed, blocked (e.g. 403), or yielded very short content (<300 chars)
        if not extracted_text or len(extracted_text.strip()) < 300:
            logger.info(f"Direct extraction insufficient (status={resp.status_code}, len={len(extracted_text) if extracted_text else 0}). Trying Jina reader fallback for: {url}")
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
                        return ArticleExtractResponse(
                            url=url,
                            success=True,
                            content=jina_content,
                            length=len(jina_content)
                        )
            except Exception as je:
                logger.warning(f"Jina reader fallback failed for {url}: {je}")

        if not extracted_text or len(extracted_text.strip()) < 100:
            logger.warning(f"Trafilatura and fallback could not extract text for URL: {url}")
            return ArticleExtractResponse(
                url=url,
                success=False,
                error=f"Extraction failed (status={resp.status_code}, no body found)"
            )

        logger.info(f"Successfully extracted {len(extracted_text)} chars from URL: {url}")
        return ArticleExtractResponse(
            url=url,
            success=True,
            content=extracted_text.strip(),
            length=len(extracted_text.strip())
        )

    except Exception as e:
        logger.error(f"Error extracting URL {url}: {str(e)}", exc_info=True)
        return ArticleExtractResponse(
            url=url,
            success=False,
            error=str(e)
        )
