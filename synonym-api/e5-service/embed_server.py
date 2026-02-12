"""
E5 유사검색 서비스 (multilingual-e5-small)
- POST /embed: 텍스트 리스트 임베딩
- POST /search: query + candidates → 유사도 순 결과
"""
import os
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List

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


@app.get("/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 5000))
    get_model()
    uvicorn.run(app, host="0.0.0.0", port=port)
