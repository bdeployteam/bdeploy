import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME, sortNodesMasterFirst } from 'src/app/models/consts';
import { InstanceConfiguration, InstanceNodeConfigurationDto, InstanceTemplateDescriptor } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ProductsService } from '../../../products/services/products.service';
import { ServersService } from '../../../servers/services/servers.service';
import { InstanceEditService } from '../../services/instance-edit.service';

@Component({
  selector: 'app-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.css'],
})
export class ConfigurationComponent implements OnInit, OnDestroy, DirtyableDialog {
  /* template */ narrow$ = new BehaviorSubject<boolean>(true);
  /* template */ headerName$ = new BehaviorSubject<string>('Loading');

  /* template */ config$ = new BehaviorSubject<InstanceConfiguration>(null);
  /* template */ serverNodes$ = new BehaviorSubject<InstanceNodeConfigurationDto[]>([]);
  /* template */ clientNode$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);

  /* template */ templates$ = new BehaviorSubject<InstanceTemplateDescriptor[]>(null);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  private subscription: Subscription;

  constructor(
    public cfg: ConfigService,
    public areas: NavAreasService,
    public servers: ServersService,
    public edit: InstanceEditService,
    private media: BreakpointObserver,
    private products: ProductsService
  ) {
    this.subscription = this.media.observe('(max-width:700px)').subscribe((bs) => this.narrow$.next(bs.matches));
  }

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([this.edit.state$, this.products.products$]).subscribe(([state, products]) => {
        if (!state || !products) {
          this.config$.next(null);
          this.serverNodes$.next([]);
          this.clientNode$.next(null);
        } else {
          this.config$.next(state.config);
          this.headerName$.next(this.edit.hasPendingChanges() || this.edit.hasSaveableChanges() ? `${state.config.name}*` : state.config.name);

          this.serverNodes$.next(state.nodeDtos.filter((p) => !this.isClientNode(p)).sort((a, b) => sortNodesMasterFirst(a.nodeName, b.nodeName)));
          this.clientNode$.next(state.nodeDtos.find((n) => this.isClientNode(n)));

          const prod = products.find((p) => p.key.name === state.config.product.name && p.key.tag === state.config.product.tag);
          if (!!prod) {
            this.templates$.next(prod.instanceTemplates);
          }
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.edit.reset();
  }

  public isDirty(): boolean {
    return this.edit.hasSaveableChanges() || this.edit.hasPendingChanges();
  }

  private isClientNode(node: InstanceNodeConfigurationDto) {
    return node.nodeName === CLIENT_NODE_NAME;
  }

  /* template */ isEmptyInstance() {
    if (!this.edit.state$.value?.nodeDtos?.length) {
      return true;
    }

    for (const node of this.edit.state$.value.nodeDtos) {
      if (!!node.nodeConfiguration?.applications?.length) {
        return false;
      }
    }
    return true;
  }

  /* template */ doTrack(index: number, node: InstanceNodeConfigurationDto) {
    return node.nodeName;
  }
}
