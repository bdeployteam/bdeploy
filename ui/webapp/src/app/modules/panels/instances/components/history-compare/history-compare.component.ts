import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { isEqual } from 'lodash-es';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME, mergeOrdererd, sortNodesMasterFirst } from 'src/app/models/consts';
import { ApplicationConfiguration, ApplicationDescriptor, InstanceConfiguration, InstanceNodeConfigurationDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { HistoryDetailsService, InstanceConfigCache } from '../../services/history-details.service';

class ApplicationPair {
  constructor(
    public base: ApplicationConfiguration,
    public compare: ApplicationConfiguration,
    public baseDesc: ApplicationDescriptor,
    public compareDesc: ApplicationDescriptor
  ) {}
}

class NodePair {
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

class HeaderPair {
  constructor(public base: InstanceConfiguration, public compare: InstanceConfiguration) {}
}

class ConfigPair {
  nodes: NodePair[] = [];
  header: HeaderPair;

  constructor(base: InstanceConfigCache, compare: InstanceConfigCache) {
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

@Component({
  selector: 'app-history-compare',
  templateUrl: './history-compare.component.html',
  styleUrls: ['./history-compare.component.css'],
})
export class HistoryCompareComponent implements OnInit, OnDestroy {
  /* template */ narrow$ = new BehaviorSubject<boolean>(false);

  /* template */ base$ = new BehaviorSubject<string>(null);
  /* template */ baseConfig$ = new BehaviorSubject<InstanceConfigCache>(null);

  /* template */ compare$ = new BehaviorSubject<string>(null);
  /* template */ compareConfig$ = new BehaviorSubject<InstanceConfigCache>(null);

  /* template */ configPair$ = new BehaviorSubject<ConfigPair>(null);

  private subscription: Subscription;

  constructor(private areas: NavAreasService, private bop: BreakpointObserver, private details: HistoryDetailsService, public instances: InstancesService) {
    this.subscription = bop.observe('(max-width: 800px)').subscribe((bs) => {
      this.narrow$.next(bs.matches);
    });

    this.subscription.add(
      this.areas.panelRoute$.subscribe((route) => {
        let base = route?.paramMap?.get('base');
        let compare = route?.paramMap?.get('compare');

        if (!base || !compare) {
          this.base$.next(null);
        } else {
          if (Number(base) > Number(compare)) {
            // swap to always have newer version RIGHT.
            [base, compare] = [compare, base];
          }

          this.base$.next(base);
          this.details.getVersionDetails(base).subscribe((config) => {
            this.baseConfig$.next(config);
          });

          this.compare$.next(compare);
          this.details.getVersionDetails(compare).subscribe((config) => {
            this.compareConfig$.next(config);
          });
        }
      })
    );

    this.subscription.add(
      combineLatest([this.baseConfig$, this.compareConfig$]).subscribe(([base, compare]) => {
        if (!!base && !!compare) {
          const pair = new ConfigPair(base, compare);
          this.configPair$.next(pair);
        } else {
          this.configPair$.next(null);
        }
      })
    );
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ getNodeName(node: NodePair) {
    return node.name === CLIENT_NODE_NAME ? 'Client Applications' : node.name;
  }

  /* template */ hasProcessControl(node: NodePair) {
    return node.name !== CLIENT_NODE_NAME;
  }
}
