import { Component, Input, OnInit, TemplateRef } from '@angular/core';
import { Difference, DiffType } from '../../services/history-diff.service';

@Component({
  selector: 'app-history-diff-field',
  templateUrl: './history-diff-field.component.html',
  styleUrls: ['./history-diff-field.component.css'],
})
export class HistoryDiffFieldComponent implements OnInit {
  @Input() diff: Difference;
  @Input() popup: TemplateRef<any>;

  @Input() maxWidthPx: number;
  @Input() maskValue = false;

  /** Which side of the diff is this field on. */
  @Input() diffSide: 'left' | 'right' | 'none' = 'none';

  /* template */ borderClass: string;
  /* template */ bgClass: string;
  /* template */ value: string;

  ngOnInit(): void {
    this.borderClass = this.getBorderClass();
    this.bgClass = this.getBgClass();
    this.value = this.getValue();
  }

  private getBorderClass(): string {
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

  private getBgClass(): string {
    return this.getBorderClass() + '-bg';
  }

  private getValue(): string {
    if (this.diff.value === null || this.diff.value === undefined) {
      return '-';
    }

    return this.maskValue
      ? '*'.repeat(this.diff.value.length)
      : this.diff.value;
  }
}
