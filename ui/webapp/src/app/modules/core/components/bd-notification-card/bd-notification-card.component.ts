import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';

@Component({
  selector: 'app-bd-notification-card',
  templateUrl: './bd-notification-card.component.html',
  styleUrls: ['./bd-notification-card.component.css'],
})
export class BdNotificationCardComponent implements OnInit {
  @Input() header: string;
  @Input() icon: string;
  @Input() warning = false;
  @Input() disabled = false;
  @Input() dismissable = true;
  @Output() dismiss = new EventEmitter<any>();

  constructor() {}

  ngOnInit(): void {}
}
