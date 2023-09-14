import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ApplicationPair, ConfigPair, NodePair } from '../../../utils/diff-utils';

@Component({
  selector: 'app-local-diff',
  templateUrl: './local-diff.component.html',
})
export class LocalDiffComponent implements OnInit, OnDestroy {
  protected edit = inject(InstanceEditService);

  protected configPair$ = new BehaviorSubject<ConfigPair>(null);
  protected clientNodeName = CLIENT_NODE_NAME;
  protected showOnlyDifferences = true;

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([
      this.edit.current$,
      this.edit.base$,
      this.edit.state$,
      this.edit.baseApplications$,
      this.edit.stateApplications$,
    ]).subscribe(([instance, base, compare, baseApps, compareApps]) => {
      if (!instance || !base || !compare) {
        return;
      }
      const baseCache = {
        config: base.config.config,
        nodes: { applications: baseApps, nodeConfigDtos: base.config.nodeDtos },
        version: `Server (${instance.instance.tag})`,
      };
      const localCache = {
        config: { ...compare.config.config },
        nodes: {
          applications: compareApps,
          nodeConfigDtos: compare.config.nodeDtos,
        },
        version: 'Local Changes',
      };

      if (this.edit.state$.value?.files?.length) {
        // config files modified.
        localCache.config.configTree = { id: 'MODIFIED' };
      }

      this.configPair$.next(new ConfigPair(baseCache, localCache));
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected showAppPair(appPair: ApplicationPair): boolean {
    const showAll = !this.showOnlyDifferences;
    return showAll || appPair.hasDifferences;
  }

  protected showNodePair(nodePair: NodePair): boolean {
    const hasApplications = !!nodePair?.applications?.length;
    const showAll = !this.showOnlyDifferences;
    return hasApplications && (showAll || nodePair.hasDifferences);
  }
}
