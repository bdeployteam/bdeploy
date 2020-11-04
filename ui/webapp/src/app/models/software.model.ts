import { ManifestKey, OperatingSystem } from './gen.dtos';

export class SoftwarePackageGroup {
  /** Name (manifest.key without OS suffix) */
  public name;

  /** Map of Operating System to sorted list of versions */
  public osVersions: Map<OperatingSystem, ManifestKey[]> = new Map();

  constructor(name: string) {
    this.name = name;
  }
}
