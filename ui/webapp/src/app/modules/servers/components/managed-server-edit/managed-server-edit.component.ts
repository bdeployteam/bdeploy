import { Component, Inject, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { cloneDeep } from 'lodash-es';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { ServerValidators } from 'src/app/modules/shared/validators/server.validator';

@Component({
  selector: 'app-managed-server-edit',
  templateUrl: './managed-server-edit.component.html',
  styleUrls: ['./managed-server-edit.component.css']
})
export class ManagedServerEditComponent implements OnInit {

  public formGroup = this.fb.group({
    description: ['', Validators.required],
    uri: ['', [Validators.required, ServerValidators.serverApiUrl]],
    auth: [''],
  });

  get descriptionControl() {
    return this.formGroup.get('description');
  }

  get uriControl() {
    return this.formGroup.get('uri');
  }

  get authControl() {
    return this.formGroup.get('auth');
  }

  constructor(
    private fb: FormBuilder,
    @Inject(MAT_DIALOG_DATA) public server: ManagedMasterDto) { }

  ngOnInit(): void {
    this.descriptionControl.setValue(this.server.description);
    this.descriptionControl.markAsTouched();
    this.uriControl.setValue(this.server.uri);
    this.uriControl.markAsTouched();
  }

  public getErrorMessage(ctrl: AbstractControl): string {
    if (ctrl.hasError('required')) {
      return 'Required';
    } else if (ctrl.hasError('uri')) {
      return ctrl.getError('uri');
    }
    return 'Unknown error';
  }

  public getResult(): ManagedMasterDto {
    const r = cloneDeep(this.server);

    r.description = this.descriptionControl.value;
    r.uri = this.uriControl.value;

    const auth = this.authControl.value;
    if (auth && auth.trim().length > 0) {
      r.auth = auth.trim();
    }
    return r;
  }

}
