# 유의어/근접어 자동 생성 (E5 기반)

## 개요

`POST /generate-proximity` 엔드포인트는 **E5(multilingual-e5-small)** 선행학습 모델을 이용해 키워드에 대한 유의어·근접어를 자동으로 생성합니다.

---

## 동작 방식 및 로직

### 1. 전체 흐름

```
[키워드] ──► E5 임베딩 ──► [후보 어휘] ──► E5 임베딩
     │                            │
     └───────► 코사인 유사도 계산 ◄──┘
                     │
                     ▼
              min_score 이상인 것만 필터
                     │
                     ▼
              유사도 상위 top_k 개 반환
```

### 2. 단계별 설명

| 단계 | 설명 |
|------|------|
| **1. 후보 어휘 로드** | `candidates`가 없으면 `product_dictionary.json`에서 모든 `display_name`과 `aliases`를 추출해 후보로 사용 |
| **2. 자기 자신 제외** | 키워드와 정규화 시 동일한 표현(띄어쓰기 제거·소문자)은 후보에서 제외 |
| **3. E5 임베딩** | `query: {keyword}`로 키워드 임베딩, `passage: {후보}`로 각 후보 임베딩 (E5 권장 prefix 사용) |
| **4. 유사도 계산** | L2 정규화된 벡터의 내적 = 코사인 유사도 (0~1) |
| **5. 필터링** | `min_score` 이상인 것만 유지, 유사도 내림차순 정렬 후 `top_k`개 반환 |

### 3. E5 모델 특성

- **multilingual-e5-small**: 한국어·영어 등 다국어 문장 임베딩
- **Query/Passage prefix**: E5는 질문에는 `"query: "`, 문서에는 `"passage: "` prefix를 붙여야 유사도가 정확함
- **임베딩**: 문장 전체를 벡터로 변환 → 의미적으로 가까운 표현이 높은 유사도

---

## API 사용법

### 요청

```
POST /generate-proximity
Content-Type: application/json

{
  "keyword": "포카리 스웨트",   // 필수: 기준 키워드
  "top_k": 10,                 // 선택: 반환 개수 (기본 10, 최대 50)
  "min_score": 0.5,            // 선택: 최소 유사도 임계값 (0~1, 기본 0.5)
  "candidates": null           // 선택: null이면 product_dictionary.json 사용
}
```

### 응답

```json
{
  "proximity_words": ["포카리", "포카리 스웨트", "환타 포도", ...],
  "results": [
    { "text": "포카리", "score": 0.8777 },
    { "text": "포카리 스웨트", "score": 0.8247 },
    ...
  ]
}
```

---

## 실행 방법

### 가상환경에서 실행

```bash
cd synonym-api/e5-service

# 가상환경 생성 (최초 1회)
python -m venv .venv

# 활성화 (Windows PowerShell)
.\.venv\Scripts\Activate.ps1

# 의존성 설치 (최초 1회)
pip install -r requirements.txt

# 서버 실행 (모델 로딩에 1~2분 소요)
python embed_server.py
```

서버는 `http://localhost:5000`에서 실행됩니다.

### 테스트 (curl 예시)

```bash
curl -X POST http://localhost:5000/generate-proximity ^
  -H "Content-Type: application/json" ^
  -d "{\"keyword\":\"포카리 스웨트\",\"top_k\":10,\"min_score\":0.5}"
```

---

## 파일 구조

| 파일 | 역할 |
|------|------|
| `embed_server.py` | FastAPI 서버. `/embed`, `/search`, `/generate-proximity`, `/health` 제공 |
| `requirements.txt` | sentence-transformers, fastapi, uvicorn |
| `PROXIMITY_GENERATE.md` | 본 문서 |

---

## 주의사항

- **후보 어휘**: `product_dictionary.json`이 없거나 `candidates`를 비우면 결과가 빈 배열
- **유사도 임계값**: `min_score`가 너무 낮으면 의미와 무관한 단어가 포함될 수 있음 (0.6~0.8 권장)
- **모델 로딩**: 서버 기동 시 E5 모델 다운로드 및 로딩에 1~2분 소요
