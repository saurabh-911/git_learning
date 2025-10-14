// SPDX-License-Identifier: MIT
pragma solidity ^0.8.23;

import "@openzeppelin/contracts/token/ERC721/extensions/ERC721URIStorage.sol";
import "@openzeppelin/contracts/utils/Counters.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract ProofMind is ERC721URIStorage, Ownable {
    using Counters for Counters.Counter;
    Counters.Counter private _tokenIds;

    struct Metadata {
        address owner;
        string dataHash; // IPFS CID
        string licenseInfo; // e.g., "CC-BY"
        uint256 timestamp;
    }

    mapping(uint256 => Metadata) private _metadatas;

    event VerifiedAndMinted(uint256 indexed tokenId, address indexed recipient, string cid, string licenseInfo);

    constructor(address initialOwner) ERC721("ProofMind", "PMD") Ownable(initialOwner) {}

    function verifyAndMint(
        address recipient,
        string calldata tokenURI_,
        string calldata dataHash,
        string calldata licenseInfo
    ) external onlyOwner returns (uint256) {
        uint256 newItemId = _tokenIds.current();
        _safeMint(recipient, newItemId);
        _setTokenURI(newItemId, tokenURI_);

        _metadatas[newItemId] = Metadata({
            owner: recipient,
            dataHash: dataHash,
            licenseInfo: licenseInfo,
            timestamp: block.timestamp
        });

        emit VerifiedAndMinted(newItemId, recipient, dataHash, licenseInfo);

        _tokenIds.increment();
        return newItemId;
    }

    function getMetadata(uint256 tokenId) external view returns (Metadata memory) {
        require(_exists(tokenId), "Nonexistent token");
        return _metadatas[tokenId];
    }
}
