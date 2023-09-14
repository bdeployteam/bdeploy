import { moveItemInArray } from '@angular/cdk/drag-drop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewEncapsulation, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
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
  private edit = inject(InstanceEditService);
  private bop = inject(BreakpointObserver);

  @Input() set group(val: ProcessControlGroupConfiguration) {
    this.group$.next(val);
  }
  @Input() node$: BehaviorSubject<InstanceNodeConfigurationDto>;
  @Input() expanded: boolean;
  @Output() expandedChange = new EventEmitter<boolean>();

  private subscription: Subscription;

  protected group$ = new BehaviorSubject<ProcessControlGroupConfiguration>(null);
  protected narrow$ = new BehaviorSubject<boolean>(true);
  protected borderClass = 'bd-status-border-none';
  protected isTop: boolean;
  protected isBottom: boolean;

  ngOnInit(): void {
    this.subscription = this.bop.observe('(max-width: 800px)').subscribe((bs) => {
      this.narrow$.next(bs.matches);
    });

    this.subscription.add(
      combineLatest([this.edit.base$, this.edit.state$, this.node$, this.group$]).subscribe(
        ([base, state, node, group]) => {
          if (!base || !state || !node || !group) {
            return;
          }

          const baseGroup = base.config.nodeDtos
            ?.find((n) => n.nodeName === node.nodeName)
            ?.nodeConfiguration?.controlGroups?.find((cg) => cg.name === group.name);
          if (!baseGroup) {
            this.borderClass = 'bd-status-border-added';
          }

          const stateIndex = state.config.nodeDtos
            .find((n) => n.nodeName === node.nodeName)
            ?.nodeConfiguration?.controlGroups?.findIndex((g) => g.name === group.name);
          const baseIndex = base.config.nodeDtos
            .find((n) => n.nodeName === node.nodeName)
            ?.nodeConfiguration?.controlGroups?.findIndex((g) => g.name === group.name);

          this.isTop = stateIndex === 0;
          this.isBottom = stateIndex === node?.nodeConfiguration?.controlGroups?.length - 1;

          if (!baseGroup) {
            return;
          }

          if (stateIndex !== baseIndex || isDirty(baseGroup, group)) {
            this.borderClass = 'bd-status-border-changed';
          } else {
            this.borderClass = 'bd-status-border-none';
          }
        }
      )
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onMoveUp() {
    const node = this.node$.value.nodeConfiguration;
    const index = node.controlGroups.indexOf(this.group$.value);
    moveItemInArray(node.controlGroups, index, node.controlGroups.indexOf(this.group$.value) - 1);
    this.edit.conceal('Move Control Group ' + this.group$.value.name);
  }

  protected onMoveDown() {
    const node = this.node$.value.nodeConfiguration;
    const index = node.controlGroups.indexOf(this.group$.value);
    moveItemInArray(node.controlGroups, index, node.controlGroups.indexOf(this.group$.value) + 1);
    this.edit.conceal('Move Control Group ' + this.group$.value.name);
  }
}
