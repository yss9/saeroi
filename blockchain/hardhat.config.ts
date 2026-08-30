import { HardhatUserConfig } from "hardhat/config";
import "@nomicfoundation/hardhat-toolbox";
import "dotenv/config";

const ssafyRpcUrl = process.env.SSAFY_RPC_URL ?? "";
const deployerPrivateKey = process.env.DEPLOYER_PRIVATE_KEY ?? "";
const ssafyChainId = process.env.SSAFY_CHAIN_ID
  ? Number(process.env.SSAFY_CHAIN_ID)
  : undefined;

const config: HardhatUserConfig = {
  solidity: {
    version: "0.8.24",
    settings: {
      optimizer: {
        enabled: true,
        runs: 200
      }
    }
  },
  networks: {
    hardhat: {
      chainId: 31337
    },
    localhost: {
      url: "http://127.0.0.1:8545",
      chainId: 31337
    },
    ssafy: {
      url: ssafyRpcUrl,
      chainId: ssafyChainId,
      accounts: deployerPrivateKey ? [deployerPrivateKey] : []
    }
  },
  paths: {
    sources: "./contracts",
    tests: "./test",
    cache: "./cache",
    artifacts: "./artifacts"
  }
};

export default config;
