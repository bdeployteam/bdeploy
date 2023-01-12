import {
  Component,
  ContentChild,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
  TemplateRef,
} from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { BdDataColumn, BdDataColumnTypeHint } from 'src/app/models/data';

@Component({
  selector: 'app-bd-data-card',
  templateUrl: './bd-data-card.component.html',
  styleUrls: ['./bd-data-card.component.css'],
})
export class BdDataCardComponent<T> implements OnInit, OnChanges {
  /**
   * The columns to display
   */
  @Input() columns: BdDataColumn<T>[];

  /**
   * The actual data. Arbitrary data which can be handled by the column definitions.
   */
  @Input() record: T;

  /**
   * A callback which can provide a route for each row. If given, each row will behave like a router link
   */
  @Input() recordRoute: (r: T) => any[];

  /**
   * A flag that denotes whether a card was selected in bulk mode
   */
  @Input() isSelected = false;

  /**
   * Event fired if a record is clicked.
   */
  @Output() recordClick = new EventEmitter<T>();

  colType: BdDataColumn<T>;
  colTitle: BdDataColumn<T>;
  colDescription: BdDataColumn<T>;
  colStatus: BdDataColumn<T>;
  colActions: BdDataColumn<T>[];
  colDetails: BdDataColumn<T>[];
  colAvatar: BdDataColumn<T>;
  colFooter: BdDataColumn<T>;

  avatar: string;

  @ContentChild('extraCardDetails') extraCardDetails: TemplateRef<any>;

  constructor(private sanitizer: DomSanitizer) {}

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
    this.colType = this.columns.find(
      (c) => c.hint === BdDataColumnTypeHint.TYPE
    );
    this.colTitle = this.columns.find(
      (c) => c.hint === BdDataColumnTypeHint.TITLE
    );
    this.colDescription = this.columns.find(
      (c) => c.hint === BdDataColumnTypeHint.DESCRIPTION
    );
    this.colStatus = this.columns.find(
      (c) => c.hint === BdDataColumnTypeHint.STATUS
    );
    this.colFooter = this.columns.find(
      (c) => c.hint === BdDataColumnTypeHint.FOOTER
    );
    this.colAvatar = this.columns.find(
      (c) => c.hint === BdDataColumnTypeHint.AVATAR
    );
    this.colActions = this.columns.filter(
      (c) => c.hint === BdDataColumnTypeHint.ACTIONS
    );
    this.colDetails = this.columns.filter(
      (c) => c.hint === BdDataColumnTypeHint.DETAILS
    );
  }

  /* template */ getImageUrl() {
    if (this.colAvatar) {
      const url = this.colAvatar.data(this.record);
      if (url) {
        return this.sanitizer.bypassSecurityTrustStyle(`url(${url})`);
      }
    }
  }
}
