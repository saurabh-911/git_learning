# ProofMind Monorepo

This repo contains:
- `contracts`: Solidity ERC-721 (Foundry) for ProofMind NFTs
- `backend`: FastAPI service for verification, Filecoin/IPFS upload, and minting
- `frontend`: React (Vite) dashboard for upload and status

## Prerequisites
- Node.js 18+
- Python 3.10+
- Foundry (forge, cast)

## Contracts
See `contracts/README.md`.

## Backend
See `backend/README.md`.

## Frontend
See `frontend/README.md`.

## Quickstart
1) Contracts
- Install deps and build:
  - `cd contracts && forge install OpenZeppelin/openzeppelin-contracts --no-commit && forge build`
- Copy `.env.example` to `.env` and populate `PRIVATE_KEY`, `OWNER_ADDRESS`, `RPC_URL`.
- Deploy to Calibration:
  - `forge script script/Deploy.s.sol:DeployScript --rpc-url "$RPC_URL" --broadcast -vvvv`

2) Backend
- `cd backend && python -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt`
- Copy `.env.example` to `.env` and set `WEB3_STORAGE_TOKEN`, `RPC_URL`, `CONTRACT_ADDRESS` (from deploy), `PRIVATE_KEY`, `OWNER_ADDRESS`.
- Run: `uvicorn app.main:app --reload --host 0.0.0.0 --port 8000`

3) Frontend
- `cd frontend && npm install`
- Copy `.env.example` to `.env` and set `VITE_API_BASE_URL` (default `http://localhost:8000`).
- Run: `npm run dev`

Open the app, connect wallet, upload a CSV. On success, you get a CID and tx hash.
