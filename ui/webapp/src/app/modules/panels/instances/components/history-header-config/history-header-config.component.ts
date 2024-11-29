import { Component, Input, OnInit, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { InstanceConfiguration } from 'src/app/models/gen.dtos';
import { DiffType, HistoryDiffService, InstanceConfigurationDiff } from '../../services/history-diff.service';

@Component({
    selector: 'app-history-header-config',
    templateUrl: './history-header-config.component.html',
    styleUrls: ['./history-header-config.component.css'],
    standalone: false
})
export class HistoryHeaderConfigComponent implements OnInit {
  private readonly diffService = inject(HistoryDiffService);

  @Input() baseConfig: InstanceConfiguration;
  @Input() compareConfig: InstanceConfiguration;

  /** Which side of the diff is this process on. */
  @Input() diffSide: 'left' | 'right' | 'none' = 'none';

  protected diff$ = new BehaviorSubject<InstanceConfigurationDiff>(null);
  protected borderClass: string | string[];

  ngOnInit(): void {
    this.diff$.next(this.diffService.diffInstanceConfig(this.baseConfig, this.compareConfig));
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
  }
}
