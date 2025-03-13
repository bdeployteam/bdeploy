import { Component, Input, OnChanges, OnInit, SimpleChanges, TemplateRef } from '@angular/core';
import { DiffType, Difference } from '../../services/history-diff.service';
import { NgClass } from '@angular/common';
import { BdPopupDirective } from '../../../../core/components/bd-popup/bd-popup.directive';

@Component({
    selector: 'app-history-diff-field',
    templateUrl: './history-diff-field.component.html',
    styleUrls: ['./history-diff-field.component.css'],
    imports: [NgClass, BdPopupDirective]
})
export class HistoryDiffFieldComponent implements OnInit, OnChanges {
  @Input() diff: Difference;
  @Input() popup: TemplateRef<unknown>;

  @Input() maxWidthPx: number;
  @Input() maskValue = false;

  /** Which side of the diff is this field on. */
  @Input() diffSide: 'left' | 'right' | 'none' = 'none';

  protected borderClass: string;
  protected bgClass: string;
  protected value: string;

  ngOnInit(): void {
    this.borderClass = this.getBorderClass();
    this.bgClass = this.getBgClass();
    this.value = this.getValue();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['diff']) {
      this.borderClass = this.getBorderClass();
      this.bgClass = this.getBgClass();
      this.value = this.getValue();
    }
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

    return this.maskValue ? '*'.repeat(this.diff.value.length) : this.diff.value;
  }
}
