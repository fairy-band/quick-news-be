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
            if resp.status_code != 200:
                logger.warning(f"HTTP {resp.status_code} for URL: {url}")
                return ArticleExtractResponse(
                    url=url,
                    success=False,
                    error=f"HTTP {resp.status_code}"
                )
            
            html_text = resp.text

        extracted_text = trafilatura.extract(
            html_text,
            include_links=False,
            include_images=False,
            include_formatting=False,
            no_fallback=False
        )

        if not extracted_text:
            logger.warning(f"Trafilatura could not extract text for URL: {url}")
            return ArticleExtractResponse(
                url=url,
                success=False,
                error="Extraction failed (no main body found)"
            )

        logger.info(f"Successfully extracted {len(extracted_text)} chars from URL: {url}")
        return ArticleExtractResponse(
            url=url,
            success=True,
            content=extracted_text,
            length=len(extracted_text)
        )

    except Exception as e:
        logger.error(f"Error extracting URL {url}: {str(e)}", exc_info=True)
        return ArticleExtractResponse(
            url=url,
            success=False,
            error=str(e)
        )
