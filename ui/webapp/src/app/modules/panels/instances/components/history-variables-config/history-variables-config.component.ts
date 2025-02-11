import { Component, inject, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { InstanceConfiguration } from 'src/app/models/gen.dtos';
import { DiffType, HistoryDiffService, VariablesDiff } from '../../services/history-diff.service';
import { NgClass, AsyncPipe } from '@angular/common';
import { BdVariableDescCardComponent } from '../../../../core/components/bd-variable-desc-card/bd-variable-desc-card.component';
import { HistoryDiffFieldComponent } from '../history-diff-field/history-diff-field.component';

@Component({
    selector: 'app-history-variables-config',
    templateUrl: './history-variables-config.component.html',
    styleUrls: ['./history-variables-config.component.css'],
    imports: [NgClass, BdVariableDescCardComponent, HistoryDiffFieldComponent, AsyncPipe]
})
export class HistoryVariablesConfigComponent implements OnInit {
  private readonly diffService = inject(HistoryDiffService);

  @Input() baseConfig: InstanceConfiguration;
  @Input() compareConfig: InstanceConfiguration;

  /** Which side of the diff is this process on. */
  @Input() diffSide: 'left' | 'right' | 'none' = 'none';

  protected diff$ = new BehaviorSubject<VariablesDiff>(null);
  protected borderClass: string | string[];

  ngOnInit(): void {
    this.diff$.next(this.diffService.diffInstanceVariables(this.baseConfig, this.compareConfig));
    this.borderClass = this.getBorderClass();
  }

  private getBorderClass(): string | string[] {
    if (this.diffSide === 'none' || this.diff$.value?.type === DiffType.UNCHANGED) {
      return 'local-border-unchanged';
    }

    if (this.diff$.value?.type === DiffType.NOT_IN_COMPARE) {
      return this.diffSide === 'right' ? 'local-border-added' : 'local-border-removed';
    } else if (this.diff$.value?.type === DiffType.NOT_IN_BASE) {
      return this.diffSide === 'right' ? 'local-border-removed' : 'local-border-added';
    } else if (this.diff$.value?.type === DiffType.CHANGED) {
      return 'local-border-changed';
    }
    return [];
  }
}
