import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Permission, UserInfo } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-user-global-permissions',
  templateUrl: './user-global-permissions.component.html',
  styleUrls: ['./user-global-permissions.component.css']
})
export class UserGlobalPermissionsComponent implements OnInit {

  public slider = 0;

  constructor(@Inject(MAT_DIALOG_DATA) public userInfo: UserInfo) { }

  public ngOnInit() {
    if (this.hasPermission(Permission.ADMIN)) {
      this.slider = 3;
    } else if (this.hasPermission(Permission.WRITE)) {
      this.slider = 2;
    } else if (this.hasPermission(Permission.READ)) {
      this.slider = 1;
    } else {
      this.slider = 0;
    }
  }

  private hasPermission(permission: Permission): boolean {
    return this.userInfo.permissions.find(c => !c.scope && c.permission === permission) != null;
  }

  public setSlider(val: number) {
    this.slider = val;
  }

  public getClass(val: number) {
    return val === this.slider ? 'label-selected' : 'label-unselected';
  }

  public getResult(): UserInfo {
    this.userInfo.permissions = this.userInfo.permissions.filter(c => c.scope);
    switch (this.slider) {
      case 1:
        this.userInfo.permissions.push({scope: null, permission: Permission.READ});
        break;
      case 2:
        this.userInfo.permissions.push({scope: null, permission: Permission.WRITE});
        break;
      case 3:
        this.userInfo.permissions.push({scope: null, permission: Permission.ADMIN});
        break;
    }
    return this.userInfo;
  }

}
