import { InstanceConfiguration, InstanceNodeConfigurationListDto } from 'src/app/models/gen.dtos';

export interface InstanceConfigCache {
  version: string;
  config: InstanceConfiguration;
  nodes: InstanceNodeConfigurationListDto;
}
