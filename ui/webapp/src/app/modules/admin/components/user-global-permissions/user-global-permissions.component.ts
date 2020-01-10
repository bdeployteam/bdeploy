import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material';
import { Capability, ScopedCapability, UserInfo } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-user-global-permissions',
  templateUrl: './user-global-permissions.component.html',
  styleUrls: ['./user-global-permissions.component.css']
})
export class UserGlobalPermissionsComponent implements OnInit {

  public admin: boolean;
  public read: boolean;
  public write: boolean;

  constructor(@Inject(MAT_DIALOG_DATA) public userInfo: UserInfo) { }

  public ngOnInit() {
    console.log('userInfo = ' + JSON.stringify(this.userInfo, null, '\t'));
    this.admin = this.hasCapability(Capability.ADMIN);
    this.read = this.hasCapability(Capability.READ);
    this.write = this.hasCapability(Capability.WRITE);
  }

  public getResult(): UserInfo {
    this.updateCapability(Capability.ADMIN, this.admin);
    this.updateCapability(Capability.READ, this.read);
    this.updateCapability(Capability.WRITE, this.write);
    return this.userInfo;
  }

  private hasCapability(capability: Capability): boolean {
    return this.userInfo.capabilities.find(c => !c.scope && c.capability === capability) != null;
  }

  private updateCapability(capabiltiy: Capability, newValue: boolean) {
    const curValue = this.hasCapability(capabiltiy);
    if ( curValue && !newValue) {
      this.userInfo.capabilities = this.userInfo.capabilities.filter(c => c.scope || c.capability !== capabiltiy);
    } else if (!curValue && newValue) {
      const sc: ScopedCapability = {scope: null, capability: capabiltiy};
      this.userInfo.capabilities.push(sc);
    }
  }
}
