import { Component, HostBinding, Input, OnInit } from '@angular/core';

@Component({
  selector: 'app-user-avatar',
  templateUrl: './user-avatar.component.html',
  styleUrls: ['./user-avatar.component.css'],
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

  @Input()
  public gravatar = false;

  constructor() {}

  ngOnInit() {}

  getStyle(s: number) {
    return {
      width: `${s}px`,
      height: `${s}px`,
      'font-size': `${s}px`,
    };
  }
}
