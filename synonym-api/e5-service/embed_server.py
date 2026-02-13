"""
E5 유사검색 서비스 (multilingual-e5-small)
- POST /embed: 텍스트 리스트 임베딩
- POST /search: query + candidates → 유사도 순 결과
- POST /generate-proximity: 키워드 → 유의어/근접어 자동 생성
"""
import json
import os
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional

app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 모델 로드는 서버 기동 시 1회 (무거움)
model = None
QUERY_PREFIX = "query: "
PASSAGE_PREFIX = "passage: "

# product_dictionary.json 경로 (e5-service 기준 상대 경로)
PRODUCT_DICT_PATH = Path(__file__).resolve().parent.parent.parent / "app" / "src" / "main" / "assets" / "product_dictionary.json"
_PROXIMITY_CANDIDATES_CACHE: Optional[List[str]] = None


def _load_proximity_candidates() -> List[str]:
    """product_dictionary.json에서 후보 어휘(display_name + aliases) 추출."""
    global _PROXIMITY_CANDIDATES_CACHE
    if _PROXIMITY_CANDIDATES_CACHE is not None:
        return _PROXIMITY_CANDIDATES_CACHE
    if not PRODUCT_DICT_PATH.exists():
        _PROXIMITY_CANDIDATES_CACHE = []
        return []
    try:
        with open(PRODUCT_DICT_PATH, "r", encoding="utf-8") as f:
            obj = json.load(f)
        seen = set()
        candidates = []
        for class_id, v in obj.items():
            tts = (v.get("tts_ko") or class_id).strip()
            if tts and tts not in seen:
                seen.add(tts)
                candidates.append(tts)
            for alias in v.get("aliases") or []:
                a = str(alias).strip()
                if a and a not in seen:
                    seen.add(a)
                    candidates.append(a)
        _PROXIMITY_CANDIDATES_CACHE = candidates
        return candidates
    except Exception:
        _PROXIMITY_CANDIDATES_CACHE = []
        return []


def get_model():
    global model
    if model is None:
        from sentence_transformers import SentenceTransformer
        model = SentenceTransformer("intfloat/multilingual-e5-small")
    return model


class EmbedRequest(BaseModel):
    texts: List[str]


class SearchRequest(BaseModel):
    query: str = ""
    candidates: List[str] = []
    top_k: int = 5


class GenerateProximityRequest(BaseModel):
    keyword: str = ""
    top_k: int = 10
    min_score: float = 0.5
    candidates: Optional[List[str]] = None  # None이면 product_dictionary.json 사용


@app.post("/embed")
def embed(req: EmbedRequest):
    """Body: { "texts": ["문장1", "문장2", ...] } → { "embeddings": [[...], [...]] }"""
    try:
        if not req.texts:
            raise HTTPException(status_code=400, detail="texts required")
        prefixed = [PASSAGE_PREFIX + t for t in req.texts]
        m = get_model()
        embeddings = m.encode(prefixed, normalize_embeddings=True)
        return {"embeddings": embeddings.tolist()}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/search")
def search(req: SearchRequest):
    """
    Body: { "query": "검색어", "candidates": ["후보1", "후보2", ...], "top_k": 5 }
    → { "results": [ { "text": "후보", "score": 0.9 }, ... ] }
    """
    try:
        query = (req.query or "").strip()
        candidates = req.candidates or []
        top_k = min(req.top_k or 5, len(candidates))
        if not query or not candidates:
            return {"results": []}

        m = get_model()
        query_emb = m.encode([QUERY_PREFIX + query], normalize_embeddings=True)[0]
        cand_prefixed = [PASSAGE_PREFIX + c for c in candidates]
        cand_embs = m.encode(cand_prefixed, normalize_embeddings=True)
        scores = (cand_embs @ query_emb).tolist()
        indexed = list(zip(candidates, scores))
        indexed.sort(key=lambda x: -x[1])
        results = [{"text": t, "score": round(s, 4)} for t, s in indexed[:top_k]]
        return {"results": results}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/generate-proximity")
def generate_proximity(req: GenerateProximityRequest):
    """
    키워드에 대한 유의어/근접어를 E5 임베딩 유사도로 자동 생성.
    - keyword: 기준 키워드 (상품명 등)
    - candidates: None이면 product_dictionary.json에서 로드
    - top_k: 반환할 근접어 개수 (기본 10)
    - min_score: 최소 유사도 임계값 (0~1, 기본 0.5)
    → { "proximity_words": [...], "results": [{ "text": "...", "score": 0.9 }] }
    """
    try:
        keyword = (req.keyword or "").strip()
        if not keyword:
            return {"proximity_words": [], "results": []}

        candidates = req.candidates if req.candidates is not None else _load_proximity_candidates()
        if not candidates:
            return {"proximity_words": [], "results": []}

        # 자기 자신 및 정규화 동일 제외
        kw_norm = keyword.replace(" ", "").lower()
        filtered = [c for c in candidates if c.strip() and c.strip().replace(" ", "").lower() != kw_norm]
        if not filtered:
            return {"proximity_words": [], "results": []}

        m = get_model()
        query_emb = m.encode([QUERY_PREFIX + keyword], normalize_embeddings=True)[0]
        cand_prefixed = [PASSAGE_PREFIX + c for c in filtered]
        cand_embs = m.encode(cand_prefixed, normalize_embeddings=True)
        scores = (cand_embs @ query_emb).tolist()
        indexed = list(zip(filtered, scores))
        indexed.sort(key=lambda x: -x[1])

        top_k = max(1, min(req.top_k or 10, 50))
        min_score = max(0.0, min(1.0, req.min_score or 0.5))
        results = [(t, s) for t, s in indexed[:top_k * 2] if s >= min_score][:top_k]
        proximity_words = [t for t, s in results]

        return {
            "proximity_words": proximity_words,
            "results": [{"text": t, "score": round(s, 4)} for t, s in results],
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 5000))
    get_model()
    uvicorn.run(app, host="0.0.0.0", port=port)
