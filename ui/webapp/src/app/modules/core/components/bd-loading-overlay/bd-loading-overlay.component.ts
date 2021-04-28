import { Component, HostBinding, Input, OnInit } from '@angular/core';
import { delayedFadeIn, delayedFadeOut } from '../../animations/fades';

@Component({
  selector: 'app-bd-loading-overlay',
  templateUrl: './bd-loading-overlay.component.html',
  styleUrls: ['./bd-loading-overlay.component.css'],
  animations: [delayedFadeIn, delayedFadeOut],
})
export class BdLoadingOverlayComponent implements OnInit {
  @Input() show: boolean;

  @HostBinding('attr.data-cy') get dataCy() {
    return this.show ? 'loading' : 'loaded';
  }

  constructor() {}

  ngOnInit(): void {}
}
