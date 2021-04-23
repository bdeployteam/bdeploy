import { Component, Input, OnInit, TemplateRef } from '@angular/core';
import { Difference, DiffType } from '../../../services/history-diff.service';

@Component({
  selector: 'app-history-diff-field',
  templateUrl: './history-diff-field.component.html',
  styleUrls: ['./history-diff-field.component.css'],
})
export class HistoryDiffFieldComponent implements OnInit {
  @Input() diff: Difference;
  @Input() popup: TemplateRef<any>;

  /** Which side of the diff is this field on. */
  @Input() diffSide: 'left' | 'right' | 'none' = 'none';

  constructor() {}

  ngOnInit(): void {}

  /* template */ getBorderClass(): string {
    if (this.diffSide === 'none' || this.diff.type === DiffType.UNCHANGED) {
      return 'local-unchanged';
    }

    switch (this.diff.type) {
      case DiffType.NOT_IN_COMPARE:
        return this.diffSide === 'right' ? 'local-added' : 'local-removed';
      case DiffType.NOT_IN_BASE:
        return this.diffSide === 'right' ? 'local-removed' : 'local-added';
      case DiffType.CHANGED:
        return 'local-changed';
    }
  }

  /* template */ getBgClass(): string {
    return this.getBorderClass() + '-bg';
  }
}
