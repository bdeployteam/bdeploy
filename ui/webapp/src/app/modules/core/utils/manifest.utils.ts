import {
  FlattenedApplicationTemplateConfiguration,
  ManifestKey,
  MinionDto,
  OperatingSystem,
  ProductDto,
} from '../../../models/gen.dtos';

/**
 * Returns the base name of the application manifest key.
 */
export function getAppKeyName(appKey: ManifestKey) {
  const fullName = appKey.name;
  const lastSlashIdx = fullName.lastIndexOf('/');
  return fullName.substring(0, lastSlashIdx);
}

/** Returns the full target application key for a given template in a given product for application to the given node. */
export function getTemplateAppKey(
  product: ProductDto,
  template: FlattenedApplicationTemplateConfiguration,
  node: MinionDto,
) {
  return `${product.product}/${template.application}/${node.os.toLowerCase()}`;
}

/**
 * Returns the OS supported by this application
 */
export function getAppOs(appKey: ManifestKey): OperatingSystem {
  const fullName = appKey.name;
  const lastSlashIdx = fullName.lastIndexOf('/') + 1;
  const osName = fullName.substring(lastSlashIdx).toUpperCase();
  return OperatingSystem[osName as keyof typeof OperatingSystem];
}

export function updateAppOs(appKey: ManifestKey, os: OperatingSystem): ManifestKey {
  const fullName = appKey.name;
  const lastSlashIdx = fullName.lastIndexOf('/') + 1;
  const osName = fullName.substring(lastSlashIdx).toUpperCase();
  const oldOs: OperatingSystem = OperatingSystem[osName as keyof typeof OperatingSystem];
  if (oldOs === os) {
    return appKey;
  } else {
    return {
      name: fullName.substring(0, lastSlashIdx) + os.toLowerCase(),
      tag: appKey.tag,
    };
  }
}
