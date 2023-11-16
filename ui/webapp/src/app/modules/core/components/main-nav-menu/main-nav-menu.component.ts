import { animate, animateChild, group, query, state, style, transition, trigger } from '@angular/animations';
import { ChangeDetectionStrategy, Component, HostBinding, Input, inject } from '@angular/core';
import { encodeDataFilePath } from 'src/app/modules/panels/instances/utils/data-file-utils';
import { delayedFadeIn, delayedFadeOut } from '../../animations/fades';
import { scaleWidthFromZero, scaleWidthToZero } from '../../animations/sizes';
import { ActionsService } from '../../services/actions.service';
import { AuthenticationService } from '../../services/authentication.service';
import { ConfigService } from '../../services/config.service';
import { NavAreasService } from '../../services/nav-areas.service';

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
})
export class MainNavMenuComponent {
  protected cfgService = inject(ConfigService);
  protected authService = inject(AuthenticationService);
  protected areas = inject(NavAreasService);
  protected actions = inject(ActionsService);
  protected masterNode = encodeDataFilePath({ minion: 'master', path: '' });

  @Input() set expanded(val: boolean) {
    this.areas.menuMaximized$.next(!this.areas.menuMaximized$.value);
  }

  get expanded() {
    return this.areas.menuMaximized$.value;
  }

  @HostBinding('@menuOpenClose') get animationState() {
    return this.expanded ? 'open' : 'closed';
  }

  goToGitHub(): void {
    window.open('https://github.com/bdeployteam/bdeploy/releases/latest', '_blank').focus();
  }
}
