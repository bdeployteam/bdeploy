import { Component, Inject, OnInit, ViewChild } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatTable, MatTableDataSource } from '@angular/material/table';
import { cloneDeep, isEqual } from 'lodash-es';
import { CustomParameter, GroupNames, NamedParameter } from '../../../../../models/application.model';
import { ParameterValidators } from '../../../../legacy/shared/validators/parameter.validators';

/** Data to be passed to this dialog */
export class Context {
  /**
   * Parameters defined by the user
   */
  customParameters: CustomParameter[] = [];

  /**
   * Already defined parameters that can be referenced
   */
  availableParameters: NamedParameter[] = [];
}

/** Represents a single row in the table */
export class ParameterRow {
  /** The parameter displayed in the table */
  readonly value: CustomParameter;

  /** Name of the UID control */
  uidCtrlName: string;

  /** Name of the predecessor control */
  predecessorCtrlName: string;

  constructor(value: CustomParameter) {
    this.value = value;
  }
}

@Component({
  selector: 'app-application-edit-manual',
  templateUrl: './application-edit-manual.component.html',
  styleUrls: ['./application-edit-manual.component.css'],
})
export class ApplicationEditManualComponent implements OnInit {
  @ViewChild(MatTable, { static: true })
  public table: MatTable<ParameterRow>;
  public columnsToDisplay = ['uid', 'predecessor', 'actions'];
  public dataSource: MatTableDataSource<ParameterRow>;

  public isDirty = false;
  public clonedParameters: CustomParameter[];
  public availableParameters: NamedParameter[];
  public parameterGroups: string[];

  public formGroup = new FormGroup({});
  public nextControlIdx = 1;

  constructor(
    @Inject(MAT_DIALOG_DATA) public context: Context,
    public dialogRef: MatDialogRef<ApplicationEditManualComponent>
  ) {}

  ngOnInit() {
    this.dataSource = new MatTableDataSource<ParameterRow>();
    this.dataSource.filterPredicate = (p, f) => {
      if (p.value.uid.toLowerCase().indexOf(f.toLowerCase()) !== -1) {
        return true;
      }
      return false;
    };

    // Fill up the table with all parameters
    for (const param of this.context.customParameters) {
      this.dataSource.data.push(new ParameterRow(cloneDeep(param)));
    }
    this.availableParameters = cloneDeep(this.context.availableParameters);
    this.clonedParameters = cloneDeep(this.context.customParameters);

    // Group parameters
    const groups = new Set<string>();
    for (const np of this.availableParameters) {
      groups.add(np.group);
    }
    this.parameterGroups = Array.from(groups);
    this.parameterGroups.sort();

    // Build form for all parameters
    for (const row of this.dataSource.data) {
      this.createFormControl(row, this.nextControlIdx++);
    }
    this.updatePredecessors();

    // Trigger calculation if something changes
    this.formGroup.valueChanges.subscribe((val) => {
      this.updateDirtyState();
      this.updatePredecessors();
    });
  }

  createFormControl(row: ParameterRow, idx: number): any {
    row.uidCtrlName = idx + '-uid';
    row.predecessorCtrlName = idx + '-predecessor';

    const uidCtrl = new FormControl();
    uidCtrl.setValue(row.value.uid);
    uidCtrl.setValidators([
      Validators.required,
      ParameterValidators.uidPattern,
      ParameterValidators.unique((validated) => {
        return this.getReservedUids(validated);
      }),
    ]);
    uidCtrl.valueChanges.subscribe((value) => {
      row.value.uid = value;
    });

    this.formGroup.addControl(row.uidCtrlName, uidCtrl);

    const predecessorCtrl = new FormControl(row.value.predecessorUid);
    predecessorCtrl.setValidators([
      ParameterValidators.unique((validated) => {
        return this.getReferencedPredecessors(validated);
      }),
    ]);
    predecessorCtrl.valueChanges.subscribe((value) => {
      row.value.predecessorUid = value;
    });

    // Add controls and ensure both values are different
    this.formGroup.addControl(row.predecessorCtrlName, predecessorCtrl);
    this.formGroup.setValidators(ParameterValidators.notEqualValueValidator(row.uidCtrlName, row.predecessorCtrlName));
  }

  getErrorMessage(row: ParameterRow, column: string): string {
    const ctrlName = this.getFormControlName(row, column);
    const ctrl = this.formGroup.controls[ctrlName];
    if (ctrl.hasError('required')) {
      return 'Mandatory input required.';
    } else if (ctrl.hasError('uidPattern')) {
      return 'Must be a single word starting with a letter, followed by valid characters: A-Z a-z 0-9 _ - .';
    } else if (ctrl.hasError('unique') && column === 'uid') {
      return 'UID is already used by another parameter.';
    } else if (ctrl.hasError('unique') && column === 'predecessor') {
      return 'Predecessor is already referenced by another parameter.';
    } else if (ctrl.hasError('notEqualValue')) {
      return 'A parameter cannot reference itself.';
    }
    return 'Unknown error';
  }

  getParametersOfGroup(groupName: string): NamedParameter[] {
    return this.availableParameters.filter((np) => np.group === groupName);
  }

  applyFilter(filterValue: string) {
    this.dataSource.filter = filterValue.trim().toLowerCase();
  }

  updateDirtyState() {
    this.isDirty = this.formGroup.dirty || !isEqual(this.dataSource.data, this.clonedParameters);
    this.dialogRef.disableClose = this.isDirty;
  }

  addParameter() {
    const idx = this.nextControlIdx++;

    const param = new CustomParameter();
    param.uid = 'custom-param-' + idx;

    // Suggest predecessor based on what has been added
    const entries = this.dataSource.data.length;
    if (entries > 0) {
      param.predecessorUid = this.dataSource.data[entries - 1].value.uid;
    } else {
      param.predecessorUid = '';
    }

    const row = new ParameterRow(param);
    this.dataSource.data.push(row);
    this.createFormControl(row, idx);
    this.table.renderRows();
  }

  removeParameter(deleted: ParameterRow) {
    const idx = this.dataSource.data.indexOf(deleted);

    // Remove from table and from form
    this.dataSource.data.splice(idx, 1);
    this.formGroup.removeControl(deleted.uidCtrlName);
    this.formGroup.removeControl(deleted.predecessorCtrlName);

    // Find control referring to this parameter and clear it
    for (const row of this.dataSource.data) {
      const predecessorCtrl = this.formGroup.controls[row.predecessorCtrlName];
      if (predecessorCtrl.value === deleted.value.uid) {
        predecessorCtrl.setValue('');
      }
    }

    // Render the remaining parameters
    this.table.renderRows();
  }

  getFormControlName(row: ParameterRow, column: string) {
    if (column === 'uid') {
      return row.uidCtrlName;
    }
    if (column === 'predecessor') {
      return row.predecessorCtrlName;
    }
    return null;
  }

  isValid(row: ParameterRow, column: string) {
    const ctrlName = this.getFormControlName(row, column);
    const ctrl = this.formGroup.controls[ctrlName];
    if (!ctrl) {
      return true;
    }
    return ctrl.valid;
  }

  updatePredecessors() {
    // Append/Remove custom group entry
    const groupIdx = this.parameterGroups.indexOf(GroupNames.CUSTOM_PARAMETERS);
    if (this.dataSource.data.length > 0 && groupIdx === -1) {
      this.parameterGroups.push(GroupNames.CUSTOM_PARAMETERS);
    }

    // Remove all entries referring to a custom group
    // Required when the user changes an existing ID that is already referenced
    this.availableParameters = this.availableParameters.filter((np) => np.group !== GroupNames.CUSTOM_PARAMETERS);

    // Append all elements that are defined in the form
    for (const row of this.dataSource.data) {
      const ctrl = this.formGroup.controls[row.uidCtrlName];
      if (!ctrl || !ctrl.value) {
        continue;
      }
      const uid = ctrl.value;
      if (this.availableParameters.find((np) => np.uid === uid)) {
        continue;
      }
      this.availableParameters.push(new NamedParameter(uid, uid, GroupNames.CUSTOM_PARAMETERS));
    }
  }

  /** Returns a list of all predecessors that are already in use */
  getReferencedPredecessors(validated: AbstractControl): string[] {
    const alreadyDefined: string[] = [];
    for (const row of this.dataSource.data) {
      const ctrl = this.formGroup.controls[row.predecessorCtrlName];
      if (!ctrl || ctrl === validated) {
        continue;
      }
      alreadyDefined.push(ctrl.value);
    }
    return alreadyDefined;
  }

  /**
   * Returns a list of all UIDS that are already in use.
   */
  getReservedUids(validated: AbstractControl): string[] {
    const alreadyDefined: string[] = [];

    // Custom parameter might not use an ID of an predefined parameter
    for (const np of this.context.availableParameters) {
      if (np.group !== GroupNames.CUSTOM_PARAMETERS) {
        alreadyDefined.push(np.uid);
      }
    }

    // Two parameters might not use the same id
    for (const row of this.dataSource.data) {
      const ctrl = this.formGroup.controls[row.uidCtrlName];
      if (!ctrl || ctrl === validated) {
        continue;
      }
      alreadyDefined.push(ctrl.value);
    }

    return alreadyDefined;
  }

  /** Returns a list of all defined parameters  */
  getReturnValue() {
    const params: CustomParameter[] = [];
    for (const row of this.dataSource.data) {
      params.push(row.value);
    }
    return params;
  }
}
