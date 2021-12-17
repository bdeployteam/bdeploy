import { moveItemInArray } from '@angular/cdk/drag-drop';
import { AfterViewInit, Component, HostBinding, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { ApplicationConfiguration, InstanceNodeConfigurationDto, MinionDto } from 'src/app/models/gen.dtos';
import { BdDataTableComponent, DragReorderEvent } from 'src/app/modules/core/components/bd-data-table/bd-data-table.component';
import { InstanceEditService } from '../../../services/instance-edit.service';
import { ProcessesColumnsService } from '../../../services/processes-columns.service';

@Component({
  selector: 'app-config-node',
  templateUrl: './config-node.component.html',
  styleUrls: ['./config-node.component.css'],
})
export class ConfigNodeComponent implements OnInit, OnDestroy, AfterViewInit {
  @HostBinding('attr.data-cy') @Input() nodeName: string;

  /* template */ node$ = new BehaviorSubject<MinionDto>(null);
  /* template */ config$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);
  /* template */ isClientNode: boolean;
  /* template */ nodeType: string;
  /* template */ node: string;

  @ViewChild('data') data: BdDataTableComponent<any>;

  private subscription: Subscription;

  /* template */ getRecordRoute = (row: ApplicationConfiguration) => {
    return ['', { outlets: { panel: ['panels', 'instances', 'config', 'process', this.nodeName, row.uid] } }];
  };

  constructor(private edit: InstanceEditService, public columns: ProcessesColumnsService) {}

  ngOnInit(): void {
    this.subscription = this.edit.nodes$.subscribe((nodes) => {
      if (!nodes || !nodes[this.nodeName]) {
        this.node$.next(null);
      } else {
        this.node$.next(nodes[this.nodeName]);
      }
      this.isClientNode = this.nodeName === CLIENT_NODE_NAME;
      this.nodeType = this.isClientNode ? 'Virtual Node' : 'Node';
      this.node = this.isClientNode ? 'Client Applications' : this.nodeName;
    });
  }

  ngAfterViewInit(): void {
    this.subscription.add(
      this.edit.state$.subscribe((s) => {
        setTimeout(() => {
          this.config$.next(s?.config?.nodeDtos?.find((n) => n.nodeName === this.nodeName));
          this.data.update();
        });
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ onReorder(order: DragReorderEvent<ApplicationConfiguration>) {
    if (order.previousIndex === order.currentIndex) {
      return;
    }

    // this is NOT necessary, but prevents flickering while rebuilding state.
    moveItemInArray(this.config$.value.nodeConfiguration.applications, order.previousIndex, order.currentIndex);

    this.edit.conceal(`Re-arrange ${order.item.name}`, this.edit.createApplicationMove(this.config$.value.nodeName, order.previousIndex, order.currentIndex));
    this.data.update();
  }
}
