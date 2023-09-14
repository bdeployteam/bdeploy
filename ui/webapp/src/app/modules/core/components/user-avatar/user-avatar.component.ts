import { Component, HostBinding, Input, OnInit, inject } from '@angular/core';
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
  protected settings = inject(SettingsService);

  @Input()
  @HostBinding('style.width.px')
  @HostBinding('style.height.px')
  public hostSize = 40;

  @Input()
  public avatarSize = 26;

  @Input()
  public mail;

  protected hostStyle: StyleDef;
  protected avatarStyle: StyleDef;

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
