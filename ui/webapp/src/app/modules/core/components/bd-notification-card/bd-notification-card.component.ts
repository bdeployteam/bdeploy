import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { NgClass } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { BdMicroIconButtonComponent } from '../bd-micro-icon-button/bd-micro-icon-button.component';

@Component({
    selector: 'app-bd-notification-card',
    templateUrl: './bd-notification-card.component.html',
    styleUrls: ['./bd-notification-card.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgClass, MatIcon, BdMicroIconButtonComponent]
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
  @Output() dismiss = new EventEmitter<unknown>();
}
