import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { cloneDeep } from 'lodash';
import { EMPTY_USER_CHANGE_PASSWORD_DTO } from 'src/app/models/consts';
import { AuthenticationService } from '../../services/authentication.service';

@Component({
  selector: 'app-user-password',
  templateUrl: './user-password.component.html',
  styleUrls: ['./user-password.component.css']
})
export class UserPasswordComponent implements OnInit {

  public passwordForm = this.fb.group({
      currentPassword: [''],
      newPassword: ['', Validators.required],
      confirmPassword: ['', Validators.required],
    }, {
      validators: this.checkInput()
    }
  );

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: any, // provided data: data.isAdmin, data.user (string)
    private fb: FormBuilder,
    private authService: AuthenticationService,
  ) { }

  ngOnInit() {
  }

  public getResult(): Object {
    const dto = cloneDeep(EMPTY_USER_CHANGE_PASSWORD_DTO);
    dto.user = this.data.user;
    dto.currentPassword = this.passwordForm.get('currentPassword').value;
    dto.newPassword = this.passwordForm.get('newPassword').value;
    return dto;
  }

  public checkInput(): ValidatorFn {
    return (control: FormGroup): ValidationErrors | null => {
      const newPassword = control.get('newPassword').value;
      const confirmPassword = control.get('confirmPassword').value;

      const isEqual = newPassword && confirmPassword && newPassword === confirmPassword;
      if (this.data.isAdmin) {
        return isEqual ? null : {'checkInput': true};
      } else {
        const currentPassword = control.get('currentPassword').value;
        return isEqual && currentPassword && currentPassword.length > 0 ? null : {'checkInput': true};
      }
    }
  }

}
