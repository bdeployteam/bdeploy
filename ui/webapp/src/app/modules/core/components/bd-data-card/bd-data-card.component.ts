import {
  Component,
  ContentChild,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
  TemplateRef
} from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { BdDataColumn, BdDataColumnTypeHint } from 'src/app/models/data';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { BdDataComponentCellComponent } from '../bd-data-component-cell/bd-data-component-cell.component';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { BdButtonComponent } from '../bd-button/bd-button.component';
import { ClickStopPropagationDirective } from '../../directives/click-stop-propagation.directive';
import { MatCard } from '@angular/material/card';
import { RouterLinkActive, RouterLink } from '@angular/router';

@Component({
    selector: 'app-bd-data-card',
    templateUrl: './bd-data-card.component.html',
    styleUrls: ['./bd-data-card.component.css'],
    imports: [NgClass, BdDataComponentCellComponent, MatTooltip, MatIcon, NgTemplateOutlet, BdButtonComponent, ClickStopPropagationDirective, MatCard, RouterLinkActive, RouterLink]
})
export class BdDataCardComponent<T> implements OnInit, OnChanges {
  private readonly sanitizer = inject(DomSanitizer);

  /**
   * The columns to display
   */
  @Input() columns: BdDataColumn<T, unknown>[];

  /**
   * The actual data. Arbitrary data which can be handled by the column definitions.
   */
  @Input() record: T;

  /**
   * A callback which can provide a route for each row. If given, each row will behave like a router link
   */
  @Input() recordRoute: (r: T) => unknown[];

  /**
   * A flag that denotes whether a card was selected in bulk mode
   */
  @Input() isSelected = false;

  /**
   * Event fired if a record is clicked.
   */
  @Output() recordClick = new EventEmitter<T>();

  colType: BdDataColumn<T, unknown>;
  colTitle: BdDataColumn<T, unknown>;
  colDescription: BdDataColumn<T, unknown>;
  colStatus: BdDataColumn<T, unknown>;
  colActions: BdDataColumn<T, unknown>[];
  colDetails: BdDataColumn<T, unknown>[];
  colAvatar: BdDataColumn<T, unknown>;
  colFooter: BdDataColumn<T, unknown>;

  avatar: string;

  @ContentChild('extraCardDetails') extraCardDetails: TemplateRef<unknown>;

  ngOnInit(): void {
    this.calculateColumnPlacing();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['columns']) {
      this.calculateColumnPlacing();
    }
  }

  private calculateColumnPlacing() {
    // from the column definitions, find the columns we want to render...
    this.colType = this.columns.find((c) => c.hint === BdDataColumnTypeHint.TYPE);
    this.colTitle = this.columns.find((c) => c.hint === BdDataColumnTypeHint.TITLE);
    this.colDescription = this.columns.find((c) => c.hint === BdDataColumnTypeHint.DESCRIPTION);
    this.colStatus = this.columns.find((c) => c.hint === BdDataColumnTypeHint.STATUS);
    this.colFooter = this.columns.find((c) => c.hint === BdDataColumnTypeHint.FOOTER);
    this.colAvatar = this.columns.find((c) => c.hint === BdDataColumnTypeHint.AVATAR);
    this.colActions = this.columns.filter((c) => c.hint === BdDataColumnTypeHint.ACTIONS);
    this.colDetails = this.columns.filter((c) => c.hint === BdDataColumnTypeHint.DETAILS);
  }

  protected getImageUrl() {
    if (this.colAvatar) {
      const url = this.colAvatar.data(this.record);
      if (url) {
        return this.sanitizer.bypassSecurityTrustStyle(`url(${url})`);
      }
    }
    return null;
  }

  protected getDataAsStringFor(col: BdDataColumn<T, unknown>) {
    const data = col.data(this.record);
    if(data) {
      return data.toString();
    }

    return null;
  }

  protected getTooltipTextFor(col: BdDataColumn<T, unknown>) {
    if(col.tooltip) {
      return col.tooltip(this.record);
    }

    return this.getDataAsStringFor(col);
  }
}
