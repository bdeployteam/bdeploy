import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material';
import { Capability, UserInfo } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-user-global-permissions',
  templateUrl: './user-global-permissions.component.html',
  styleUrls: ['./user-global-permissions.component.css']
})
export class UserGlobalPermissionsComponent implements OnInit {

  public slider = 0;

  constructor(@Inject(MAT_DIALOG_DATA) public userInfo: UserInfo) { }

  public ngOnInit() {
    console.log('userInfo = ' + JSON.stringify(this.userInfo, null, '\t'));

    if (this.hasCapability(Capability.ADMIN)) {
      this.slider = 3;
    } else if (this.hasCapability(Capability.WRITE)) {
      this.slider = 2;
    } else if (this.hasCapability(Capability.READ)) {
      this.slider = 1;
    } else {
      this.slider = 0;
    }
  }

  private hasCapability(capability: Capability): boolean {
    return this.userInfo.capabilities.find(c => !c.scope && c.capability === capability) != null;
  }

  public setSlider(val: number) {
    this.slider = val;
  }

  public getClass(val: number) {
    return val === this.slider ? 'label-selected' : 'label-unselected';
  }

  public getResult(): UserInfo {
    this.userInfo.capabilities = this.userInfo.capabilities.filter(c => c.scope);
    switch (this.slider) {
      case 1:
        this.userInfo.capabilities.push({scope: null, capability: Capability.READ});
        break;
      case 2:
        this.userInfo.capabilities.push({scope: null, capability: Capability.WRITE});
        break;
      case 3:
        this.userInfo.capabilities.push({scope: null, capability: Capability.ADMIN});
        break;
    }
    return this.userInfo;
  }

}
