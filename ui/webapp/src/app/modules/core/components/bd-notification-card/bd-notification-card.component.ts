import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-bd-notification-card',
  templateUrl: './bd-notification-card.component.html',
  styleUrls: ['./bd-notification-card.component.css'],
})
export class BdNotificationCardComponent {
  @Input() header: string;
  @Input() icon: string;
  @Input() svgIcon: string;
  @Input() type: 'error' | 'warning' | 'generic' = 'generic';
  @Input() disabled = false;
  @Input() dismissable = true;
  @Input() background: 'toolbar' | 'dialog' = 'toolbar';
  @Input() collapsed = false;
  @Output() dismiss = new EventEmitter<any>();
}
