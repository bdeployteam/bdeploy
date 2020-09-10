import { Component, Inject, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { cloneDeep } from 'lodash-es';
import { EMPTY_USER_CHANGE_PASSWORD_DTO } from 'src/app/models/consts';

@Component({
  selector: 'app-user-password',
  templateUrl: './user-password.component.html',
  styleUrls: ['./user-password.component.css']
})
export class UserPasswordComponent implements OnInit {

  public passwordForm = this.fb.group({
      currentPassword: [''],
      passwords: this.fb.group({
        password: ['', [Validators.required]],
        passwordRepeat: ['', [Validators.required]]
      })
    }
  );

  get currentPasswordControl() {
    return this.passwordForm.get('currentPassword');
  }

  get passwordsGroup() {
    return this.passwordForm.get('passwords');
  }

  get passwordControl() {
    return this.passwordForm.get('passwords.password');
  }

  get passwordRepeatControl() {
    return this.passwordForm.get('passwords.passwordRepeat');
  }

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: any, // provided data: data.isAdmin, data.user (string)
    private fb: FormBuilder
  ) { }

  ngOnInit() {
    if (!this.data.isAdmin) {
      this.currentPasswordControl.setValidators([Validators.required]);
    }
    this.passwordsGroup.setValidators([this.passwordsEqual()]);
  }

  public getResult(): Object {
    const dto = cloneDeep(EMPTY_USER_CHANGE_PASSWORD_DTO);
    dto.user = this.data.user;
    dto.currentPassword = this.currentPasswordControl.value;
    dto.newPassword = this.passwordControl.value;
    return dto;
  }

  public passwordsEqual(): ValidatorFn {
    return (control: FormGroup): ValidationErrors | null => {
      const pw1 = this.passwordControl.value;
      const pw2 = this.passwordRepeatControl.value;
      const ok = pw1 && pw2 && pw1 === pw2;
      return ok ? null : {'passwordsNotEqual' : true};
    };
  }

  public getErrorMessage(ctrl: AbstractControl): string {
    if (ctrl.hasError('required')) {
      return 'Required';
    }
    return 'Unknown error';
  }

}
