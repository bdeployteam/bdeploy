import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { InstanceNodeConfigurationDto, InstanceNodeConfigurationListDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { HistoryDetailsService } from '../../services/history-details.service';
import { InstanceConfigCache } from '../../utils/instance-utils';

@Component({
  selector: 'app-history-view',
  templateUrl: './history-view.component.html',
  styleUrls: ['./history-view.component.css'],
})
export class HistoryViewComponent implements OnInit, OnDestroy {
  /* template */ narrow$ = new BehaviorSubject<boolean>(false);
  /* template */ base$ = new BehaviorSubject<string>(null);
  /* template */ config$ = new BehaviorSubject<InstanceConfigCache>(null);

  private subscription: Subscription;

  constructor(private areas: NavAreasService, private bop: BreakpointObserver, private details: HistoryDetailsService, public instances: InstancesService) {
    this.subscription = bop.observe('(max-width: 800px)').subscribe((bs) => {
      this.narrow$.next(bs.matches);
    });

    this.subscription.add(
      this.areas.panelRoute$.subscribe((route) => {
        const base = route?.paramMap?.get('base');
        if (!base) {
          this.base$.next(null);
        } else {
          this.base$.next(base);
          this.details.getVersionDetails(base).subscribe((config) => {
            this.config$.next(config);
          });
        }
      })
    );
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ getNodeName(node: InstanceNodeConfigurationDto) {
    return node?.nodeName === CLIENT_NODE_NAME ? 'Client Applications' : node?.nodeName;
  }

  /* template */ hasProcessControl(node: InstanceNodeConfigurationDto) {
    return node?.nodeName !== CLIENT_NODE_NAME;
  }

  /* template */ getAppDesc(nodes: InstanceNodeConfigurationListDto, name: string) {
    return nodes?.applications.find((a) => a.key.name === name)?.descriptor;
  }
}
