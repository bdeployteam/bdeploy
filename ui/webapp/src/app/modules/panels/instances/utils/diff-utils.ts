import { isEqual } from 'lodash-es';
import { mergeOrdererd, sortNodesMasterFirst } from 'src/app/models/consts';
import {
  ApplicationConfiguration,
  ApplicationDescriptor,
  ApplicationDto,
  InstanceConfiguration,
  InstanceNodeConfigurationDto,
  NodeType,
} from 'src/app/models/gen.dtos';
import { ApplicationConfigurationDiff, DiffType } from '../services/history-diff.service';
import { InstanceConfigCache } from './instance-utils';

export class ApplicationPair {
  hasDifferences: boolean;

  constructor(
    public base: ApplicationConfiguration,
    public compare: ApplicationConfiguration,
    public baseDesc: ApplicationDescriptor,
    public compareDesc: ApplicationDescriptor,
  ) {
    const left = new ApplicationConfigurationDiff(base, compare, baseDesc);
    const right = new ApplicationConfigurationDiff(compare, base, compareDesc);
    this.hasDifferences = left.type !== DiffType.UNCHANGED || right.type !== DiffType.UNCHANGED;
  }
}

export class NodePair {
  name: string;
  type: NodeType;
  isOrderChanged: boolean;
  applications: ApplicationPair[] = [];
  hasDifferences: boolean;

  constructor(
    base: InstanceNodeConfigurationDto,
    compare: InstanceNodeConfigurationDto,
    baseApplications: ApplicationDto[],
    compareApplications: ApplicationDto[],
  ) {
    this.name = base?.nodeName ? base.nodeName : compare?.nodeName;
    this.type = base?.nodeConfiguration.nodeType ? base.nodeConfiguration.nodeType : compare.nodeConfiguration.nodeType;

    const baseApps = base?.nodeConfiguration?.applications ? base.nodeConfiguration.applications : [];
    const compareApps = compare?.nodeConfiguration?.applications ? compare.nodeConfiguration.applications : [];

    const compIds = compareApps.map((a) => a.id);
    const baseIds = baseApps.map((a) => a.id);

    const compMatching = compIds?.filter((e) => !!baseIds.includes(e));
    const baseMatching = baseIds?.filter((e) => !!compIds.includes(e));
    this.isOrderChanged = !isEqual(compMatching, baseMatching);

    const order = mergeOrdererd(compIds, baseIds, (x: string) => x);

    for (const appId of order) {
      const baseApp = baseApps.find((a) => a.id === appId);
      const compareApp = compareApps.find((a) => a.id === appId);

      this.applications.push(
        new ApplicationPair(
          baseApp,
          compareApp,
          baseApplications ? baseApplications.find((a) => a.key.name === baseApp?.application?.name)?.descriptor : null,
          compareApplications
            ? compareApplications.find((a) => a.key.name === compareApp?.application?.name)?.descriptor
            : null,
        ),
      );
    }

    this.hasDifferences = this.applications.some((appPair) => appPair.hasDifferences);
  }
}

export class HeaderPair {
  constructor(
    public base: InstanceConfiguration,
    public compare: InstanceConfiguration,
  ) {}
}

export class ConfigPair {
  nodes: NodePair[] = [];
  header: HeaderPair;

  constructor(
    public base: InstanceConfigCache,
    public compare: InstanceConfigCache,
  ) {
    this.header = new HeaderPair(base?.config, compare?.config);

    const sortedNodes = base?.nodes?.nodeConfigDtos ? [...base.nodes.nodeConfigDtos] : [];
    if (compare?.nodes?.nodeConfigDtos) {
      for (const node of compare.nodes.nodeConfigDtos) {
        if (!sortedNodes.find((n) => n.nodeName === node.nodeName)) {
          sortedNodes.push(node);
        }
      }
    }
    sortedNodes.sort((a, b) => sortNodesMasterFirst(a.nodeName, b.nodeName));

    for (const node of sortedNodes) {
      const baseNode = base?.nodes?.nodeConfigDtos?.find((n) => n.nodeName === node.nodeName);
      const compareNode = compare?.nodes?.nodeConfigDtos?.find((n) => n.nodeName === node.nodeName);

      this.nodes.push(new NodePair(baseNode, compareNode, base?.nodes?.applications, compare?.nodes?.applications));
    }
  }
}
