import { moveItemInArray } from '@angular/cdk/drag-drop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewEncapsulation } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { InstanceNodeConfigurationDto, ProcessControlGroupConfiguration } from 'src/app/models/gen.dtos';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { InstanceEditService } from '../../../../services/instance-edit.service';

@Component({
  selector: 'app-control-group',
  templateUrl: './control-group.component.html',
  styleUrls: ['./control-group.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class ControlGroupComponent implements OnInit, OnDestroy {
  @Input() public group: ProcessControlGroupConfiguration;
  @Input() public node$: BehaviorSubject<InstanceNodeConfigurationDto>;
  @Input() public expanded: boolean;
  @Output() public expandedChange = new EventEmitter<boolean>();

  private subscription: Subscription;

  /* template */ narrow$ = new BehaviorSubject<boolean>(true);
  /* template */ borderClass = 'bd-status-border-none';
  /* template */ isTop: boolean;
  /* template */ isBottom: boolean;

  constructor(private edit: InstanceEditService, private bop: BreakpointObserver) {
    this.subscription = bop.observe('(max-width: 800px)').subscribe((bs) => {
      this.narrow$.next(bs.matches);
    });
  }

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([this.edit.base$, this.edit.state$, this.node$]).subscribe(([base, state, node]) => {
        if (!base || !state || !node) {
          return;
        }

        const baseGroup = base.config.nodeDtos
          ?.find((n) => n.nodeName === node.nodeName)
          ?.nodeConfiguration?.controlGroups?.find((cg) => cg.name === this.group.name);
        if (!baseGroup) {
          this.borderClass = 'bd-status-border-added';
        }

        const stateIndex = state.config.nodeDtos
          .find((n) => n.nodeName === node.nodeName)
          .nodeConfiguration.controlGroups.findIndex((g) => g.name === this.group.name);
        const baseIndex = base.config.nodeDtos
          .find((n) => n.nodeName === node.nodeName)
          .nodeConfiguration.controlGroups.findIndex((g) => g.name === this.group.name);

        this.isTop = stateIndex === 0;
        this.isBottom = stateIndex === node.nodeConfiguration.controlGroups.length - 1;

        if (!baseGroup) {
          return;
        }

        if (stateIndex !== baseIndex || isDirty(baseGroup, this.group)) {
          this.borderClass = 'bd-status-border-changed';
        } else {
          this.borderClass = 'bd-status-border-none';
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ onMoveUp() {
    const node = this.node$.value.nodeConfiguration;
    const index = node.controlGroups.indexOf(this.group);
    moveItemInArray(node.controlGroups, index, node.controlGroups.indexOf(this.group) - 1);
    this.edit.conceal('Move Control Group ' + this.group.name);
  }

  /* template */ onMoveDown() {
    const node = this.node$.value.nodeConfiguration;
    const index = node.controlGroups.indexOf(this.group);
    moveItemInArray(node.controlGroups, index, node.controlGroups.indexOf(this.group) + 1);
    this.edit.conceal('Move Control Group ' + this.group.name);
  }
}
