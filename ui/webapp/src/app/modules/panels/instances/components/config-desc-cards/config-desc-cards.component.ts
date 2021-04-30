import { Component, Input, OnInit } from '@angular/core';

export type AppBaseFields = 'cfg.name';
export type AppCommandFields = 'cfg.executable';
export type AppProcessCtrlFields =
  | 'cfg.control.startType'
  | 'cfg.control.keepAlive'
  | 'cfg.control.noRetries'
  | 'cfg.control.gracePeriod'
  | 'cfg.control.attachStdin';

export type AppEndpointFields =
  | 'cfg.ep.path'
  | 'cfg.ep.port'
  | 'cfg.ep.secure'
  | 'cfg.ep.trustAll'
  | 'cfg.ep.trustStore'
  | 'cfg.ep.trustStorePass'
  | 'cfg.ep.authType'
  | 'cfg.ep.authUser'
  | 'cfg.ep.authPass';

export type InstFields = 'inst.name' | 'inst.description' | 'inst.purpose' | 'inst.productTag' | 'inst.autoStart' | 'inst.autoUninstall' | 'inst.configTree';

export type AllFields = AppBaseFields | AppProcessCtrlFields | AppCommandFields | AppEndpointFields | InstFields;

@Component({
  selector: 'app-config-desc-cards',
  templateUrl: './config-desc-cards.component.html',
  styleUrls: ['./config-desc-cards.component.css'],
})
export class ConfigDescCardsComponent implements OnInit {
  @Input() field: AllFields;

  constructor() {}

  ngOnInit(): void {}
}
