import { Component, HostBinding, Input, OnInit } from '@angular/core';
import { SettingsService } from '../../services/settings.service';

export interface StyleDef {
  width: string;
  height: string;
  fontSize: string;
}

@Component({
  selector: 'app-user-avatar',
  templateUrl: './user-avatar.component.html',
})
export class UserAvatarComponent implements OnInit {
  @Input()
  @HostBinding('style.width.px')
  @HostBinding('style.height.px')
  public hostSize = 40;

  @Input()
  public avatarSize = 26;

  @Input()
  public mail;

  /* template */ hostStyle: StyleDef;
  /* template */ avatarStyle: StyleDef;

  constructor(public settings: SettingsService) {}

  ngOnInit() {
    this.hostStyle = this.getStyle(this.hostSize);
    this.avatarStyle = this.getStyle(this.avatarSize);
  }

  private getStyle(s: number): StyleDef {
    return {
      width: `${s}px`,
      height: `${s}px`,
      fontSize: `${s}px`,
    };
  }
}
