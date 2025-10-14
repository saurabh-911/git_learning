// SPDX-License-Identifier: MIT
pragma solidity ^0.8.23;

import "forge-std/Script.sol";
import "forge-std/console2.sol";
import {ProofMind} from "src/ProofMind.sol";

contract DeployScript is Script {
    function run() external {
        uint256 deployerPrivateKey = vm.envUint("PRIVATE_KEY");
        address initialOwner = vm.envAddress("OWNER_ADDRESS");

        vm.startBroadcast(deployerPrivateKey);
        ProofMind proofMind = new ProofMind(initialOwner);
        vm.stopBroadcast();

        console2.log("ProofMind deployed at:", address(proofMind));
    }
}
