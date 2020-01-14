import { Component, ElementRef, Inject, OnInit, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material';
import { cloneDeep } from 'lodash';
import { EMPTY_USER_INFO } from 'src/app/models/consts';
import { UserInfo } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-user-edit',
  templateUrl: './user-edit.component.html',
  styleUrls: ['./user-edit.component.css']
})
export class UserEditComponent implements OnInit {

  @ViewChild('pass1', { static: false })
  private pass1: ElementRef;

  @ViewChild('pass2', { static: false })
  private pass2: ElementRef;

  public isCreate = false;

  constructor(@Inject(MAT_DIALOG_DATA) public user: UserInfo) {
  }

  ngOnInit() {
    if (!this.user) {
      this.isCreate = true;
      this.user = cloneDeep(EMPTY_USER_INFO);
    }
  }

  passwordsSame() {
    if (this.isCreate && this.user.password) {
      return this.pass1 && this.pass2 && this.pass1.nativeElement.value === this.pass2.nativeElement.value;
    }
    return true;
  }

}
