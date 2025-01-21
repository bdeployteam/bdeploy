import { Component, HostBinding, Input } from '@angular/core';
import { delayedFadeIn, delayedFadeOut } from '../../animations/fades';
import { MatProgressSpinner } from '@angular/material/progress-spinner';

@Component({
    selector: 'app-bd-loading-overlay',
    templateUrl: './bd-loading-overlay.component.html',
    animations: [delayedFadeIn, delayedFadeOut],
    imports: [MatProgressSpinner],
})
export class BdLoadingOverlayComponent {
  @Input() show: boolean;
  @Input() mode: 'dim' | 'hide' = 'dim';

  @HostBinding('attr.data-cy') get dataCy() {
    return this.show ? 'loading' : 'loaded';
  }
}
