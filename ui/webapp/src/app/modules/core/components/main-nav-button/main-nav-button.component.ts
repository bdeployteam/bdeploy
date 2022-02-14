import {
  animate,
  state,
  style,
  transition,
  trigger,
} from '@angular/animations';
import { Component, Input, ViewChild } from '@angular/core';
import { RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-main-nav-button',
  templateUrl: './main-nav-button.component.html',
  animations: [
    trigger('showHide', [
      state(
        'visible',
        style({ display: 'flex', transform: 'translateX(0px)', opacity: 1 })
      ),
      state(
        'hidden',
        style({ display: 'none', transform: 'translateX(-50px)', opacity: 0 })
      ),
      transition('visible => hidden', animate('0.2s ease')),
      transition('hidden => visible', [
        style({ display: 'flex' }),
        animate('0.2s ease'),
      ]),
    ]),
  ],
})
export class MainNavButtonComponent {
  @Input() icon: string;
  @Input() svgIcon: string;
  @Input() text: string;
  @Input() collapsed: boolean;
  @Input() disabled: boolean;
  @Input() visible = true;
  @Input() route: any[];
  @Input() panel = false;

  @ViewChild(RouterLinkActive, { static: false }) /* template */
  rla: RouterLinkActive;
}
