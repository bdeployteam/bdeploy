import { AfterViewInit, Component, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { ApplicationConfiguration, InstanceNodeConfigurationDto, MinionDto } from 'src/app/models/gen.dtos';
import { BdDataDisplayComponent } from 'src/app/modules/core/components/bd-data-display/bd-data-display.component';
import { InstanceEditService } from '../../../services/instance-edit.service';
import { ProcessesColumnsService } from '../../../services/processes-columns.service';

@Component({
  selector: 'app-config-node',
  templateUrl: './config-node.component.html',
  styleUrls: ['./config-node.component.css'],
})
export class ConfigNodeComponent implements OnInit, OnDestroy, AfterViewInit {
  @Input() nodeName: string;

  /* template */ node$ = new BehaviorSubject<MinionDto>(null);
  /* template */ config$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);

  @ViewChild('data') data: BdDataDisplayComponent<any>;

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
    });
  }

  ngAfterViewInit(): void {
    this.subscription.add(
      this.edit.state$.subscribe((s) => {
        setTimeout(() => {
          this.config$.next(s?.nodeDtos?.find((n) => n.nodeName === this.nodeName));
          this.data.update();
        });
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ isClientNode() {
    return this.nodeName === CLIENT_NODE_NAME;
  }

  /* template */ getNodeType() {
    return this.isClientNode() ? 'Virtual Node' : 'Node';
  }

  /* template */ getNodeName() {
    return this.isClientNode() ? 'Client Applications' : this.nodeName;
  }
}
