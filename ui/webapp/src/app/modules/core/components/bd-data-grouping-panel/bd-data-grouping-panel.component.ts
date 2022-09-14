import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
} from '@angular/core';
import { MatCheckboxChange } from '@angular/material/checkbox';
import { Subscription } from 'rxjs';
import {
  BdDataGrouping,
  BdDataGroupingDefinition,
  bdExtractGroups,
  bdSortGroups,
  UNMATCHED_GROUP,
} from 'src/app/models/data';

/**
 * A single grouping panel, providing a drop dow to choose the definition,
 * and subsequently checkboxes to select/de-seelect certain group values.
 */
@Component({
  selector: 'app-bd-data-grouping-panel',
  templateUrl: './bd-data-grouping-panel.component.html',
  styleUrls: ['./bd-data-grouping-panel.component.css'],
})
export class BdDataGroupingPanelComponent<T>
  implements OnInit, OnChanges, OnDestroy
{
  /** The available grouping definitions */
  @Input() definitions: BdDataGroupingDefinition<T>[];
  /** The records currently available for grouping */
  @Input() records: T[];
  /** Binds the emitter for the popup-open event, so this panel can refresh values */
  @Input() popupEmitter: EventEmitter<any>;
  /** The panel's index, this is used to show a hint to the user. */
  @Input() index: number;
  /** The actual grouping bound to the controls */
  @Input() grouping: BdDataGrouping<T> = { definition: null, selected: [] };
  /** Whether grouping can be removed is calculated by the parent component */
  @Input() removeDisabled: boolean;
  /** Emitted whenever the grouping is changed by the user. */
  @Output() groupingChange = new EventEmitter<BdDataGrouping<T>>();
  /** Emitted whenever remove button is clicked by the user */
  @Output() removeClicked = new EventEmitter<BdDataGrouping<T>>();

  /* template */ noGroup = UNMATCHED_GROUP;
  /* template */ groupingValues: string[];
  /* template */ filter: string;
  /* template */ get filteredGroupingValues(): string[] {
    if (!this.filter) {
      return this.groupingValues;
    }
    return this.groupingValues.filter(
      (gv) => gv && gv.toLowerCase().includes(this.filter.toLowerCase())
    );
  }

  private subscription: Subscription;

  constructor(private cd: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.subscription = this.popupEmitter.subscribe(() => {
      // whenever the parent popup is shown, we need to update our values as the table contents may have changed.
      this.updateGroupingValues();
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  ngOnChanges(): void {
    this.updateGroupingValues();
  }

  updateGroupingValues() {
    // calculate possible values for the grouping.
    if (!!this.grouping?.definition && this.records?.length) {
      this.groupingValues = bdExtractGroups(
        this.grouping.definition,
        this.records
      ).sort(
        this.grouping.definition.sort
          ? this.grouping.definition.sort
          : bdSortGroups
      );

      // remove any "stale" grouping from the current setting (i.e. row value no longer present)
      if (this.grouping.selected?.length) {
        this.grouping.selected = this.grouping.selected.filter((val) =>
          this.groupingValues.includes(val)
        );

        if (this.grouping.selected.length === this.groupingValues.length) {
          this.grouping.selected = [];
        }
      }

      // selected values should show up first
      this.groupingValues.sort((a, b) =>
        this.grouping.selected.includes(a)
          ? -1
          : this.grouping.selected.includes(b)
          ? 1
          : 0
      );
    }
  }

  /* template */ setGrouping(def: BdDataGroupingDefinition<T>) {
    if (def === this.grouping.definition) {
      // the same thing, don't re-load and trigger.
    }

    this.grouping = { definition: def, selected: [] };

    // trigger change in grouping. this will re-load the component.
    this.groupingChange.emit(this.grouping);
  }

  /* template */ groupCheckChanged(group: string, change: MatCheckboxChange) {
    if (!this.grouping || !this.grouping.definition) {
      return;
    }

    if (!change.checked) {
      // deselecting. if list is empty this means we have to add all but ours.
      if (!this.grouping.selected.length) {
        // no need to copy, will be done next.
        this.grouping.selected = this.groupingValues;
      }

      this.grouping.selected = this.grouping.selected.filter(
        (g) => g !== group
      );

      if (!this.grouping.selected.length) {
        // after de-selection, grouping is empty -> all checkboxes will be selected, need to re-select
        // the current toggled checkbox, as this will not happen automatically.
        setTimeout(() => (change.source.checked = true));
      }
    } else {
      // selecting. if all groups are selected, we can empty the list.
      this.grouping.selected.push(group);
      if (this.grouping.selected.length === this.groupingValues.length) {
        this.grouping.selected = [];
      }
    }

    this.groupingChange.emit(this.grouping);
  }

  /* template */ removeGrouping(): void {
    if (!this.removeDisabled) {
      this.removeClicked.emit(this.grouping);
    }
  }

  /* template */ groupingLevelNumber(level: number): string {
    // level will never reach 21. So this should be ok
    switch (level) {
      case 1:
        return '1st';
      case 2:
        return '2nd';
      case 3:
        return '3rd';
      default:
        return level + 'th';
    }
  }
}
