import { Component, inject, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ApplicationConfiguration, ApplicationDescriptor } from 'src/app/models/gen.dtos';
import { ApplicationConfigurationDiff, DiffType, HistoryDiffService } from '../../services/history-diff.service';
import { NgClass, AsyncPipe } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { ConfigDescCardsComponent } from '../config-desc-cards/config-desc-cards.component';
import { HistoryDiffFieldComponent } from '../history-diff-field/history-diff-field.component';
import { ParamDescCardComponent } from '../param-desc-card/param-desc-card.component';

@Component({
    selector: 'app-history-process-config',
    templateUrl: './history-process-config.component.html',
    styleUrls: ['./history-process-config.component.css'],
    imports: [NgClass, MatIcon, ConfigDescCardsComponent, HistoryDiffFieldComponent, ParamDescCardComponent, AsyncPipe]
})
export class HistoryProcessConfigComponent implements OnInit {
  private readonly diffService = inject(HistoryDiffService);

  @Input() baseConfig: ApplicationConfiguration;
  @Input() compareConfig: ApplicationConfiguration;

  @Input() baseDescriptor: ApplicationDescriptor;
  @Input() hasProcessControl = true;
  @Input() onlyCommand = false;

  /** Which side of the diff is this process on. */
  @Input() diffSide: 'left' | 'right' | 'none' = 'none';

  protected diff$ = new BehaviorSubject<ApplicationConfigurationDiff>(null);

  ngOnInit(): void {
    this.update();
  }

  public update(): void {
    this.diff$.next(this.diffService.diffAppConfig(this.baseConfig, this.compareConfig, this.baseDescriptor));
  }

  protected getBorderClass(diffType: DiffType): string | string[] {
    if (this.onlyCommand) {
      return [];
    }

    if (this.diffSide === 'none' || diffType === DiffType.UNCHANGED) {
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
