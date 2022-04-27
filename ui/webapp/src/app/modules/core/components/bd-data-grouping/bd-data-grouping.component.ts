import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { CustomDataGrouping } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { calculateGrouping } from 'src/app/modules/core/utils/preset.utils';

@Component({
  selector: 'app-bd-data-grouping',
  templateUrl: './bd-data-grouping.component.html',
})
export class BdDataGroupingComponent<T> implements OnInit, OnChanges {
  /** whether mutiple groupings are supported */
  @Input() multiple = true;

  /** If set, allows saving the grouping in the local storage of the browser, using the given key */
  @Input() presetKey: string;

  /** The definitions of available groupings */
  @Input() definitions: BdDataGroupingDefinition<T>[];

  /** A callback providing the possible values for a grouping definition, which is likely very dependent on the actual data displayed in another component. */
  @Input() records: T[];

  /** The initial grouping which should be used if nothing has been saved. */
  @Input() defaultGrouping: BdDataGrouping<T>[];

  @Input() hasGlobalPreset = false;

  /** Emitted when the grouping changes. This may be emitted once after creating the component if a preset is loaded. */
  @Output() groupingChange = new EventEmitter<BdDataGrouping<T>[]>();

  @Output() globalPresetSaved = new EventEmitter<CustomDataGrouping[]>();

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  /* template */ groupings: BdDataGrouping<T>[] = [];
  /* template */ filteredGroups: BdDataGrouping<T>[];

  constructor(
    private snackBar: MatSnackBar,
    public auth: AuthenticationService
  ) {}

  ngOnInit(): void {
    this.loadPreset();
    this.fireUpdate();
  }

  ngOnChanges(changes: SimpleChanges) {
    // we support changing the preset key, so we can re-use the same widget and
    // have different grouping keys for table and card views.
    if (changes['multiple']) {
      this.loadPreset();
      this.fireUpdate();
    }

    if (changes['records']) {
      // in case the previous set of records was completely empty, we need to reload
      // default grouping (etc.) for the stored values to be available (potentially).
      if (!changes['records'].previousValue?.length) {
        this.loadPreset();
        this.fireUpdate();
      }
    }
  }

  private getStorageKey() {
    return 'grouping-' + (this.multiple ? 'm-' : 's-') + this.presetKey;
  }

  private loadPreset() {
    // always start with a single entry with no grouping selected.
    if (this.defaultGrouping?.length) {
      this.groupings = [...this.defaultGrouping];
    } else {
      this.groupings = [{ definition: null, selected: [] }];
    }

    if (!this.presetKey) {
      return;
    }

    // load the preset from the local storage
    const stored = localStorage.getItem(this.getStorageKey());
    if (!stored) {
      return;
    }

    // deserialize the stored preset.
    try {
      const parsed = JSON.parse(stored) as CustomDataGrouping[];
      const restored: BdDataGrouping<T>[] = calculateGrouping(
        this.definitions,
        parsed
      );
      if (restored?.length) {
        this.groupings = restored;
      }
    } catch (e) {
      console.error('Cannot load grouping preset', e);
    }
    this.filteredGroups = this.getFilteredGroups();
  }

  private groupingToPreset(): CustomDataGrouping[] {
    return this.groupings
      .filter((g) => !!g.definition)
      .map(
        (g) =>
          ({
            name: g.definition.name,
            selected: g.selected,
          } as CustomDataGrouping)
      );
  }

  /* template */ savePreset() {
    localStorage.setItem(
      this.getStorageKey(),
      JSON.stringify(this.groupingToPreset())
    );

    this.snackBar.open('Preset saved in local browser.', null, {
      duration: 1500,
    });
  }

  saveGlobalPreset() {
    this.dialog
      .confirm(
        'Save global preset?',
        'This will set as the global default preset for all users.'
      )
      .subscribe((confirmed) => {
        if (confirmed) {
          this.globalPresetSaved.emit(this.groupingToPreset());
        }
      });
  }

  /* template */ addGrouping() {
    this.groupings.push({ definition: null, selected: [] });
    this.filteredGroups = this.getFilteredGroups();
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
    this.filteredGroups = this.getFilteredGroups();
    this.groupingChange.emit(this.filteredGroups);
  }

  private getFilteredGroups(): BdDataGrouping<T>[] {
    return this.groupings.filter((g) => !!g.definition);
  }
}
