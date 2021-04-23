import { Component, Input, OnInit } from '@angular/core';

export type BaseFields = 'cfg.name';
export type CommandFields = 'cfg.executable';
export type ProcessCtrlFields =
  | 'cfg.control.startType'
  | 'cfg.control.keepAlive'
  | 'cfg.control.noRetries'
  | 'cfg.control.gracePeriod'
  | 'cfg.control.attachStdin';

export type AllFields = BaseFields | ProcessCtrlFields | CommandFields;

@Component({
  selector: 'app-process-config-desc-cards',
  templateUrl: './process-config-desc-cards.component.html',
  styleUrls: ['./process-config-desc-cards.component.css'],
})
export class ProcessConfigDescCardsComponent implements OnInit {
  @Input() field: AllFields;

  constructor() {}

  ngOnInit(): void {}
}
