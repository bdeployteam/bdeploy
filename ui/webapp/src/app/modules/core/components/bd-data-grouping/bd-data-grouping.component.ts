import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild,
  ViewEncapsulation,
} from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { CustomDataGrouping } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { calculateGrouping } from 'src/app/modules/core/utils/preset.utils';

enum PresetType {
  PERSONAL = 'PERSONAL',
  GLOBAL = 'GLOBAL',
}

@Component({
  selector: 'app-bd-data-grouping',
  templateUrl: './bd-data-grouping.component.html',
  styleUrls: ['./bd-data-grouping.component.css'],
  encapsulation: ViewEncapsulation.None,
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
  /* template */ presetType: PresetType;
  /* template */ presetTypes = [PresetType.GLOBAL, PresetType.PERSONAL];
  /* template */ get availableDefinitions(): BdDataGroupingDefinition<T>[] {
    const selectedDefinitions = this.groupings.map((g) => g.definition);
    return this.definitions.filter((def) => !selectedDefinitions.includes(def));
  }
  /* template */ get groupBy(): string {
    const groupings =
      this.groupings
        .map((g) => g.definition?.name)
        .filter((item) => !!item)
        .join(', ') || 'N/A';
    const groupBy = `Group By: ${groupings}`;
    return groupBy.length > 30 ? `${groupBy.substring(0, 30)}...` : groupBy;
  }

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
    this.loadPresetType();
    // always start with a single entry with no grouping selected.
    if (this.defaultGrouping?.length) {
      this.groupings = [...this.defaultGrouping];
    } else {
      this.groupings = [{ definition: null, selected: [] }];
    }

    if (this.presetType === PresetType.GLOBAL || !this.presetKey) {
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
      } else {
        this.groupings = [{ definition: null, selected: [] }];
      }
    } catch (e) {
      console.error('Cannot load grouping preset', e);
    }
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

  /* template */ capitalize(val: string): string {
    return val[0].toUpperCase() + val.substring(1).toLowerCase();
  }

  /* template */ setPresetType(presetType: PresetType): void {
    this.presetType = presetType;
    localStorage.setItem(this.presetTypeKey(), this.presetType);
    this.loadPreset();
  }

  private presetTypeKey(): string {
    return 'preset-type-' + (this.multiple ? 'm-' : 's-') + this.presetKey;
  }

  private loadPresetType() {
    const storedPresetType = localStorage.getItem(this.presetTypeKey());
    const storedPreset = localStorage.getItem(this.getStorageKey());
    if (storedPresetType) {
      this.presetType = storedPresetType as PresetType;
    } else if (storedPreset) {
      this.presetType = PresetType.PERSONAL;
    } else {
      this.presetType = PresetType.GLOBAL;
    }
  }

  /* template */ savePreset(): void {
    switch (this.presetType) {
      case PresetType.GLOBAL:
        this.saveGlobalPreset();
        break;
      case PresetType.PERSONAL:
        this.saveLocalPreset();
        break;
    }
  }

  private saveGlobalPreset() {
    const preset = this.groupingToPreset();
    this.dialog
      .confirm(
        'Save global preset?',
        'This will grouping will be set as the global default preset for all users.'
      )
      .subscribe((confirmed) => {
        if (confirmed) {
          this.globalPresetSaved.emit(preset);
        }
      });
  }

  private saveLocalPreset() {
    localStorage.setItem(
      this.getStorageKey(),
      JSON.stringify(this.groupingToPreset())
    );

    this.snackBar.open('Preset saved in local browser.', null, {
      duration: 1500,
    });
  }

  /* template */ deletePreset(): void {
    switch (this.presetType) {
      case PresetType.GLOBAL:
        this.globalPresetSaved.emit(null);
        break;
      case PresetType.PERSONAL:
        this.deletePresetFromLocalStorage();
        this.setPresetType(PresetType.GLOBAL);
        break;
    }
  }

  private deletePresetFromLocalStorage() {
    localStorage.removeItem(this.getStorageKey());

    this.snackBar.open('Preset deleted from local browser.', null, {
      duration: 1500,
    });
  }

  /* template */ addGrouping() {
    this.groupings.push({ definition: null, selected: [] });
  }

  /* template */ removeGrouping(grouping: BdDataGrouping<T>) {
    this.groupings.splice(this.groupings.indexOf(grouping), 1);

    // add an empty one, so there is always a panel visible.
    if (!this.groupings.length) {
      this.addGrouping();
    }

    this.fireUpdate();
  }

  /* template */ onDrop(event: CdkDragDrop<BdDataGrouping<T>[]>) {
    moveItemInArray(this.groupings, event.previousIndex, event.currentIndex);
    this.fireUpdate();
  }

  /* template */ fireUpdate() {
    const filteredGroups = this.groupings.filter((g) => !!g.definition);
    this.groupingChange.emit(filteredGroups);
  }
}
