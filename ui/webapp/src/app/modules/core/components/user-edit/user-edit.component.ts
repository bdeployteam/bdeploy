import { Component, ElementRef, Inject, OnInit, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { cloneDeep } from 'lodash';
import { EMPTY_USER_INFO } from 'src/app/models/consts';
import { UserInfo } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-user-edit',
  templateUrl: './user-edit.component.html',
  styleUrls: ['./user-edit.component.css']
})
export class UserEditComponent implements OnInit {

  @ViewChild('pass1')
  private pass1: ElementRef;

  @ViewChild('pass2')
  private pass2: ElementRef;

  public isCreate = false;
  public user: UserInfo;
  public knownUser: string[] = null;

  constructor(@Inject(MAT_DIALOG_DATA) public data: any) {
  }

  ngOnInit() {
    if (this.data) {
      this.isCreate = this.data.isCreate ? this.data.isCreate : false;
      this.user = this.data.user ? this.data.user : cloneDeep(EMPTY_USER_INFO);
    }
    if (this.isCreate) {
      this.knownUser = this.data.knownUser;
    }
  }

  isValid() {
    if (this.isCreate) {
      if (!this.user.name || !this.user.password) {
        return false;
      }
      const passwordsValid = this.pass1 && this.pass2 && this.pass1.nativeElement.value === this.pass2.nativeElement.value;
      const userExists = this.knownUser && this.knownUser.find(u => u === this.user.name);
      return passwordsValid && !userExists;
    }
    return true;
  }

}
