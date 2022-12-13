import { Component, HostBinding, Input } from '@angular/core';
import { delayedFadeIn, delayedFadeOut } from '../../animations/fades';

@Component({
  selector: 'app-bd-loading-overlay',
  templateUrl: './bd-loading-overlay.component.html',
  animations: [delayedFadeIn, delayedFadeOut],
})
export class BdLoadingOverlayComponent {
  @Input() show: boolean;
  @Input() mode: 'dim' | 'hide' = 'dim';

  @HostBinding('attr.data-cy') get dataCy() {
    return this.show ? 'loading' : 'loaded';
  }
}
