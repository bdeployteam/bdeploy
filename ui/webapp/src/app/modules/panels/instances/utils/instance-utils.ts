import {
  InstanceConfiguration,
  InstanceNodeConfigurationDto,
  InstanceNodeConfigurationListDto,
  ProcessControlGroupConfiguration,
  ProcessControlGroupHandlingType,
  ProcessControlGroupWaitType,
} from 'src/app/models/gen.dtos';

export interface InstanceConfigCache {
  version: string;
  config: InstanceConfiguration;
  nodes: InstanceNodeConfigurationListDto;
}

export const DEF_CONTROL_GROUP: ProcessControlGroupConfiguration = {
  name: 'Default',
  processOrder: [],
  startType: ProcessControlGroupHandlingType.PARALLEL,
  startWait: ProcessControlGroupWaitType.CONTINUE,
  stopType: ProcessControlGroupHandlingType.SEQUENTIAL,
};

export function getNodeOfApplication(nodes: InstanceNodeConfigurationDto[], uid: string): InstanceNodeConfigurationDto {
  for (const node of nodes) {
    const app = node.nodeConfiguration.applications.find((a) => a.uid === uid);
    if (!!app) {
      return node;
    }
  }
  return null;
}

/**
 * Retrieves the Process Control Group for an Application. As a fallback for updated Systems, the Default Control Group is returned if none is found.
 */
export function getProcessControlGroupOfApplication(groups: ProcessControlGroupConfiguration[], uid: string): ProcessControlGroupConfiguration {
  for (const group of groups) {
    const app = group.processOrder.find((a) => a === uid);
    if (!!app) {
      return group;
    }
  }
  return DEF_CONTROL_GROUP;
}
