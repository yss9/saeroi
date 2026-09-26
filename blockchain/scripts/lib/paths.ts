import path from "node:path";

export const PROJECT_ROOT = path.resolve(__dirname, "..", "..");

export function deploymentFile(networkName: string): string {
  return path.join(PROJECT_ROOT, "deployments", `${networkName}.json`);
}

export function abiFile(contractName: string): string {
  return path.join(PROJECT_ROOT, "abi", `${contractName}.json`);
}

