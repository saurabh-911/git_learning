import io
import json
import os
from typing import Optional

import orjson
import pandas as pd
from dotenv import load_dotenv
from evidently.metric_preset import DataDriftPreset, DataQualityPreset
from evidently.report import Report
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from web3 import Web3
from w3storage import API as W3S

load_dotenv()

PORT = int(os.getenv("PORT", "8000"))
CORS_ORIGINS = os.getenv("CORS_ORIGINS", "*").split(",")

WEB3_STORAGE_TOKEN = os.getenv("WEB3_STORAGE_TOKEN")
RPC_URL = os.getenv("RPC_URL")
CONTRACT_ADDRESS = os.getenv("CONTRACT_ADDRESS")
PRIVATE_KEY = os.getenv("PRIVATE_KEY")
OWNER_ADDRESS = os.getenv("OWNER_ADDRESS")

if not WEB3_STORAGE_TOKEN:
    print("WARNING: WEB3_STORAGE_TOKEN not set; uploads will fail.")

app = FastAPI(title="ProofMind API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[origin.strip() for origin in CORS_ORIGINS] if CORS_ORIGINS else ["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class VerifyResponse(BaseModel):
    cid: Optional[str]
    tx_hash: Optional[str]
    verification: str
    nft_minted: bool
    report: Optional[dict]


# Web3 client setup (lazy)
w3: Optional[Web3] = None
contract = None


def get_w3_and_contract():
    global w3, contract
    if w3 is None:
        if not RPC_URL:
            raise RuntimeError("RPC_URL is not configured")
        w3 = Web3(Web3.HTTPProvider(RPC_URL))
        if not w3.is_connected():
            raise RuntimeError("Failed to connect to RPC")
    if contract is None:
        if not CONTRACT_ADDRESS:
            raise RuntimeError("CONTRACT_ADDRESS is not configured")
        # Minimal ABI for only the function we call
        abi = [
            {
                "inputs": [
                    {"internalType": "address", "name": "recipient", "type": "address"},
                    {"internalType": "string", "name": "tokenURI_", "type": "string"},
                    {"internalType": "string", "name": "dataHash", "type": "string"},
                    {"internalType": "string", "name": "licenseInfo", "type": "string"},
                ],
                "name": "verifyAndMint",
                "outputs": [{"internalType": "uint256", "name": "", "type": "uint256"}],
                "stateMutability": "nonpayable",
                "type": "function",
            }
        ]
        contract = w3.eth.contract(address=Web3.to_checksum_address(CONTRACT_ADDRESS), abi=abi)
    return w3, contract


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/upload", response_model=VerifyResponse)
async def upload_dataset(file: UploadFile = File(...), license: str = "CC-BY"):
    if not file.filename:
        raise HTTPException(400, "No file provided")

    content = await file.read()

    # Try CSV parse
    try:
        df = pd.read_csv(io.BytesIO(content))
    except Exception:
        raise HTTPException(400, "Only CSV files are supported in this demo")

    # Duplicates check
    duplicate_rows = int(df.duplicated().sum())
    if duplicate_rows > 0:
        return VerifyResponse(
            cid=None,
            tx_hash=None,
            verification=f"Fail: {duplicate_rows} duplicate rows detected",
            nft_minted=False,
            report=None,
        )

    # Simple data quality and drift report using Evidently
    if len(df) >= 4:
        midpoint = len(df) // 2
        reference_df = df.iloc[:midpoint]
        current_df = df.iloc[midpoint:]
    else:
        reference_df = df
        current_df = df.copy()

    report = Report(metrics=[DataQualityPreset(), DataDriftPreset()])
    report.run(reference_data=reference_df, current_data=current_df)
    report_dict = report.as_dict()

    # Upload to web3.storage
    if not WEB3_STORAGE_TOKEN:
        raise HTTPException(500, "WEB3_STORAGE_TOKEN missing")

    w3s = W3S(token=WEB3_STORAGE_TOKEN)
    cid = w3s.post_upload([("file", (file.filename, content))])["cid"]

    # Build and send transaction to mint
    w3, contract = get_w3_and_contract()
    if not PRIVATE_KEY or not OWNER_ADDRESS:
        raise HTTPException(500, "Minting config missing: PRIVATE_KEY or OWNER_ADDRESS")

    sender = Web3.to_checksum_address(OWNER_ADDRESS)

    # Prepare transaction data
    try:
        nonce = w3.eth.get_transaction_count(sender)
        tx = contract.functions.verifyAndMint(
            sender,
            f"ipfs://{cid}",
            cid,
            license,
        ).build_transaction(
            {
                "from": sender,
                "nonce": nonce,
                "gas": 800000,
                "maxFeePerGas": w3.to_wei("100", "gwei"),
                "maxPriorityFeePerGas": w3.to_wei("2", "gwei"),
            }
        )
        signed = w3.eth.account.sign_transaction(tx, private_key=PRIVATE_KEY)
        tx_hash = w3.eth.send_raw_transaction(signed.rawTransaction)
        receipt = w3.eth.wait_for_transaction_receipt(tx_hash)
        minted = receipt.status == 1
        return VerifyResponse(
            cid=cid,
            tx_hash=tx_hash.hex(),
            verification="Passed",
            nft_minted=minted,
            report=report_dict,
        )
    except Exception as exc:
        raise HTTPException(500, f"Mint failed: {exc}")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=PORT, reload=True)
