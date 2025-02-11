import { animate, animateChild, group, query, state, style, transition, trigger } from '@angular/animations';
import { ChangeDetectionStrategy, Component, HostBinding, inject } from '@angular/core';
import { encodeFilePath } from 'src/app/modules/panels/instances/utils/data-file-utils';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';
import { delayedFadeIn, delayedFadeOut } from '../../animations/fades';
import { scaleWidthFromZero, scaleWidthToZero } from '../../animations/sizes';
import { ActionsService } from '../../services/actions.service';
import { AuthenticationService } from '../../services/authentication.service';
import { ConfigService } from '../../services/config.service';
import { NavAreasService } from '../../services/nav-areas.service';
import { MatCard } from '@angular/material/card';
import { BdActionsComponent } from '../bd-actions/bd-actions.component';
import { NgClass, AsyncPipe } from '@angular/common';
import { MainNavButtonComponent } from '../main-nav-button/main-nav-button.component';
import { MatDivider } from '@angular/material/divider';
import { MatTooltip } from '@angular/material/tooltip';
import { BdPopupDirective } from '../bd-popup/bd-popup.directive';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { BdButtonComponent } from '../bd-button/bd-button.component';
import { VersionPipe } from '../../pipes/version.pipe';
import { VersionShortPipe } from '../../pipes/version-short.pipe';

@Component({
    selector: 'app-main-nav-menu',
    templateUrl: './main-nav-menu.component.html',
    styleUrls: ['./main-nav-menu.component.css', './main-nav-menu-hamburger.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    animations: [
        delayedFadeIn,
        delayedFadeOut,
        scaleWidthFromZero,
        scaleWidthToZero,
        trigger('menuOpenClose', [
            state('closed', style({ width: '64px' })),
            state('open', style({ width: '220px' })),
            transition('open => closed', [
                group([animate('0.2s ease', style({ width: '64px' })), query('@*', [animateChild()])]),
            ]),
            transition('closed => open', [
                group([animate('0.2s ease', style({ width: '220px' })), query('@*', [animateChild()])]),
            ]),
        ]),
        trigger('headerOpenClose', [
            state('closed', style({ width: '104px' })),
            state('open', style({ width: '192px' })),
            transition('open => closed', [animate('0.2s ease')]),
            transition('closed => open', [animate('0.2s ease')]),
        ]),
    ],
    imports: [MatCard, BdActionsComponent, NgClass, MainNavButtonComponent, MatDivider, MatTooltip, BdPopupDirective, MatProgressSpinner, BdButtonComponent, AsyncPipe, VersionPipe, VersionShortPipe]
})
export class MainNavMenuComponent {
  protected readonly cfgService = inject(ConfigService);
  protected readonly authService = inject(AuthenticationService);
  protected readonly areas = inject(NavAreasService);
  protected readonly actions = inject(ActionsService);
  protected readonly reports = inject(ReportsService);

  protected masterNode = encodeFilePath({ minion: 'master', path: '' });

  @HostBinding('@menuOpenClose') get animationState() {
    return this.areas.menuMaximized$.value ? 'open' : 'closed';
  }

  protected goToInstanceConfiguration() {
    this.areas.navigateBoth(
      ['/instances', 'configuration', this.areas.groupContext$.value, this.areas.instanceContext$.value],
      ['panels', 'instances', 'settings'],
    );
  }

  goToGitHub(): void {
    window.open('https://github.com/bdeployteam/bdeploy/releases/latest', '_blank', 'noreferrer').focus();
  }
}
