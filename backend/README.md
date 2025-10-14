# ProofMind Backend (FastAPI)

## Setup
```
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# Fill .env values
```

## Run
```
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## Endpoints
- GET `/health`
- POST `/upload` (multipart form: `file`, optional `license`)

Returns JSON: `{ cid, tx_hash, verification, nft_minted, report }`
