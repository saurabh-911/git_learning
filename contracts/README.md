# ProofMind Contracts

## Prerequisites
- Foundry: https://book.getfoundry.sh/getting-started/installation

## Install
```
forge install OpenZeppelin/openzeppelin-contracts --no-commit
```

## Build
```
forge build
```

## Deploy (Calibration)
Create `.env` by copying `.env.example` and filling values.
```
source .env
forge script script/Deploy.s.sol:DeployScript \
  --rpc-url "$RPC_URL" \
  --broadcast \
  -vvvv
```
