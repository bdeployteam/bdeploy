import { Component, Input } from '@angular/core';

export type AppBaseFields = 'cfg.name' | 'cfg.id';

export type AppCommandFields = 'cfg.executable';

export type AppProcessCtrlFields =
  | 'cfg.control.startType'
  | 'cfg.control.keepAlive'
  | 'cfg.control.noRetries'
  | 'cfg.control.gracePeriod'
  | 'cfg.control.attachStdin'
  | 'cfg.control.autostart';

export type AppEndpointFields =
  | 'cfg.ep.path'
  | 'cfg.ep.port'
  | 'cfg.ep.secure'
  | 'cfg.ep.trustAll'
  | 'cfg.ep.trustStore'
  | 'cfg.ep.trustStorePass'
  | 'cfg.ep.authType'
  | 'cfg.ep.authUser'
  | 'cfg.ep.authPass'
  | 'cfg.ep.tokenUrl'
  | 'cfg.ep.clientId'
  | 'cfg.ep.clientSecret';

export type InstFields =
  | 'inst.name'
  | 'inst.description'
  | 'inst.purpose'
  | 'inst.productTag'
  | 'inst.autoStart'
  | 'inst.autoUninstall'
  | 'inst.configTree'
  | 'inst.systemId'
  | 'inst.systemTag';

export type AllFields = AppBaseFields | AppProcessCtrlFields | AppCommandFields | AppEndpointFields | InstFields;

@Component({
    selector: 'app-config-desc-cards',
    templateUrl: './config-desc-cards.component.html',
    standalone: false
})
export class ConfigDescCardsComponent {
  @Input() field: AllFields;
}
