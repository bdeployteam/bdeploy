import { Component, Inject, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { cloneDeep } from 'lodash-es';
import { UserChangePasswordDto, UserInfo } from 'src/app/models/gen.dtos';
import { UserValidators } from 'src/app/modules/admin/validators/user.validator';

export const EMPTY_USER_INFO: UserInfo = {
  name: null,
  password: null,
  fullName: null,
  email: null,
  external: false,
  externalSystem: null,
  externalTag: null,
  inactive: false,
  lastActiveLogin: null,
  permissions: [],
};

export const EMPTY_USER_CHANGE_PASSWORD_DTO: UserChangePasswordDto = {
  user: null,
  currentPassword: null,
  newPassword: null,
};

@Component({
  selector: 'app-user-edit',
  templateUrl: './user-edit.component.html',
  styleUrls: ['./user-edit.component.css'],
})
export class UserEditComponent implements OnInit {
  public userFormGroup = this.fb.group({
    name: [''],
    fullName: [''],
    email: [''],
    passwords: this.fb.group({
      password: [''],
      passwordRepeat: [''],
    }),
  });

  get nameControl() {
    return this.userFormGroup.get('name');
  }

  get fullNameControl() {
    return this.userFormGroup.get('fullName');
  }

  get emailControl() {
    return this.userFormGroup.get('email');
  }

  get passwordsGroup() {
    return this.userFormGroup.get('passwords');
  }

  get passwordControl() {
    return this.userFormGroup.get('passwords.password');
  }

  get passwordRepeatControl() {
    return this.userFormGroup.get('passwords.passwordRepeat');
  }

  public isCreate = false;
  public isExternal = false;
  public knownUser: string[] = null;

  constructor(private fb: FormBuilder, @Inject(MAT_DIALOG_DATA) public data: any) {}

  ngOnInit() {
    if (this.data) {
      this.isCreate = this.data.isCreate ? this.data.isCreate : false;
      if (this.data.user) {
        this.isExternal = this.data.user.external;
        this.nameControl.setValue(this.data.user.name);
        this.fullNameControl.setValue(this.data.user.fullName);
        this.emailControl.setValue(this.data.user.email);
      }
    }
    if (this.isCreate) {
      this.knownUser = this.data.knownUser ? this.data.knownUser : [];
      this.nameControl.setValidators([Validators.required, UserValidators.namePattern, this.forbiddenNamesValidator()]);
      this.passwordControl.setValidators([Validators.required]);
      this.passwordRepeatControl.setValidators([Validators.required]);
      this.passwordsGroup.setValidators([this.passwordsEqual()]);
    }
  }

  public forbiddenNamesValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const userExists = this.knownUser.find((u) => u === control.value);
      return userExists ? { forbiddenName: true } : null;
    };
  }

  public passwordsEqual(): ValidatorFn {
    return (control: FormGroup): ValidationErrors | null => {
      const pw1 = this.passwordControl.value;
      const pw2 = this.passwordRepeatControl.value;
      const ok = pw1 && pw2 && pw1 === pw2;
      return ok ? null : { passwordsNotEqual: true };
    };
  }

  public getErrorMessage(ctrl: AbstractControl): string {
    if (ctrl.hasError('required')) {
      return 'Required';
    } else if (ctrl.hasError('namePattern')) {
      return 'Must be a single word starting with a letter or digit, followed by valid characters: A-Z a-z 0-9 _ - .';
    } else if (ctrl.hasError('forbiddenName')) {
      return 'User already exists';
    }
    return 'Unknown error';
  }

  public getResult(): UserInfo {
    const r = this.isCreate ? cloneDeep(EMPTY_USER_INFO) : cloneDeep(this.data.user);

    r.fullName = this.fullNameControl.value;
    r.email = this.emailControl.value;

    if (this.isCreate) {
      r.name = this.nameControl.value;
      r.password = this.passwordControl.value;
    }
    return r;
  }
}
