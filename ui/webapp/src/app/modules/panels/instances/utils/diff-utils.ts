import { isEqual } from 'lodash-es';
import { mergeOrdererd, sortNodesMasterFirst } from 'src/app/models/consts';
import { ApplicationConfiguration, ApplicationDescriptor, InstanceConfiguration, InstanceNodeConfigurationDto } from 'src/app/models/gen.dtos';
import { InstanceConfigCache } from './instance-utils';

export class ApplicationPair {
  constructor(
    public base: ApplicationConfiguration,
    public compare: ApplicationConfiguration,
    public baseDesc: ApplicationDescriptor,
    public compareDesc: ApplicationDescriptor
  ) {}
}

export class NodePair {
  name: string;
  isOrderChanged: boolean;
  applications: ApplicationPair[] = [];

  constructor(
    base: InstanceNodeConfigurationDto,
    compare: InstanceNodeConfigurationDto,
    baseApplications: { [index: string]: ApplicationDescriptor },
    compareApplications: { [index: string]: ApplicationDescriptor }
  ) {
    this.name = base?.nodeName ? base.nodeName : compare?.nodeName;

    const baseApps = base?.nodeConfiguration?.applications ? base.nodeConfiguration.applications : [];
    const compareApps = compare?.nodeConfiguration?.applications ? compare.nodeConfiguration.applications : [];

    const compUids = compareApps.map((a) => a.uid);
    const baseUids = baseApps.map((a) => a.uid);

    const compMatching = compUids?.filter((e) => !!baseUids.includes(e));
    const baseMatching = baseUids?.filter((e) => !!compUids.includes(e));
    this.isOrderChanged = !isEqual(compMatching, baseMatching);

    const order = mergeOrdererd(compUids, baseUids, (x) => x);

    for (const appUid of order) {
      const baseApp = baseApps.find((a) => a.uid === appUid);
      const compareApp = compareApps.find((a) => a.uid === appUid);

      this.applications.push(
        new ApplicationPair(
          baseApp,
          compareApp,
          baseApplications ? baseApplications[baseApp?.application?.name] : null,
          compareApplications ? compareApplications[compareApp?.application?.name] : null
        )
      );
    }
  }
}

export class HeaderPair {
  constructor(public base: InstanceConfiguration, public compare: InstanceConfiguration) {}
}

export class ConfigPair {
  nodes: NodePair[] = [];
  header: HeaderPair;

  constructor(public base: InstanceConfigCache, public compare: InstanceConfigCache) {
    this.header = new HeaderPair(base?.config, compare?.config);

    const sortedNodes = !!base?.nodes?.nodeConfigDtos ? [...base.nodes.nodeConfigDtos] : [];
    if (!!compare?.nodes?.nodeConfigDtos) {
      for (const node of compare?.nodes?.nodeConfigDtos) {
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
