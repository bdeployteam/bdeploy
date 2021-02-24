import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { ErrorMessage, LoggingService } from '../../services/logging.service';

interface BdDataGroupingStorage {
  name: string;
  selected: string[];
}

@Component({
  selector: 'app-bd-data-grouping',
  templateUrl: './bd-data-grouping.component.html',
  styleUrls: ['./bd-data-grouping.component.css'],
})
export class BdDataGroupingComponent<T> implements OnInit, OnChanges {
  private log = this.logging.getLogger('BdDataGroupingComponent');

  /** whether mutiple groupings are supported */
  @Input() multiple = true;

  /** If set, allows saving the grouping in the local storage of the browser, using the given key */
  @Input() presetKey: string;

  /** The definitions of available groupings */
  @Input() definitions: BdDataGroupingDefinition<T>[];

  /** A callback providing the possible values for a grouping definition, which is likely very dependent on the actual data displayed in another component. */
  @Input() records: T[];

  /** Emitted when the grouping changes. This may be emitted once after creating the component if a preset is loaded. */
  @Output() groupingChange = new EventEmitter<BdDataGrouping<T>[]>();

  groupings: BdDataGrouping<T>[] = [];

  constructor(private logging: LoggingService, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.loadPreset();
    this.fireUpdate();
  }

  ngOnChanges(changes: SimpleChanges) {
    // we support changing the preset key, so we can re-use the same widget and
    // have different grouping keys for table and card views.
    if (!!changes['multiple']) {
      this.loadPreset();
      this.fireUpdate();
    }
  }

  private getStorageKey() {
    return 'grouping-' + (this.multiple ? 'm-' : 's-') + this.presetKey;
  }

  private loadPreset() {
    // always start with a single entry with no grouping selected.
    this.groupings = [{ definition: null, selected: [] }];

    if (!this.presetKey) {
      return;
    }

    // load the preset from the local storage
    const stored = localStorage.getItem(this.getStorageKey());
    if (!stored) {
      return;
    }

    // deserialize the stored preset.
    const restored: BdDataGrouping<T>[] = [];
    try {
      const parsed = JSON.parse(stored) as BdDataGroupingStorage[];
      if (!!parsed?.length) {
        for (const item of parsed) {
          const def = this.definitions.find((d) => d.name === item.name);
          if (!def) {
            this.log.warn('Grouping definition not (any longer?) available: ' + item.name);
            continue;
          }
          restored.push({ definition: def, selected: item.selected });
        }
      }

      if (!!restored?.length) {
        this.groupings = restored;
      }
    } catch (e) {
      this.log.error(new ErrorMessage('Cannot load grouping preset', e));
    }
  }

  /* template */ savePreset() {
    localStorage.setItem(
      this.getStorageKey(),
      JSON.stringify(
        this.groupings
          .filter((g) => !!g.definition)
          .map((g) => ({ name: g.definition.name, selected: g.selected } as BdDataGroupingStorage))
      )
    );

    this.snackBar.open('Preset saved in local browser.', null, { duration: 1500 });
  }

  /* template */ addGrouping() {
    this.groupings.push({ definition: null, selected: [] });
  }

  /* template */ removeGrouping() {
    this.groupings.pop();

    // add an empty one, so there is always a panel visible.
    if (!this.groupings.length) {
      this.addGrouping();
    }

    this.fireUpdate();
  }

  /* template */ fireUpdate() {
    this.groupingChange.emit(this.getFilteredGroups());
  }

  /* template */ getFilteredGroups(): BdDataGrouping<T>[] {
    return this.groupings.filter((g) => !!g.definition);
  }
}
