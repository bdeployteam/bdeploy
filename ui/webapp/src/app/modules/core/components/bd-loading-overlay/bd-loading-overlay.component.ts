import { Component, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { delayedFadeIn, delayedFadeOut } from '../../animations/fades';

@Component({
  selector: 'app-bd-loading-overlay',
  templateUrl: './bd-loading-overlay.component.html',
  styleUrls: ['./bd-loading-overlay.component.css'],
  animations: [delayedFadeIn, delayedFadeOut],
})
export class BdLoadingOverlayComponent implements OnInit {
  @Input() showWhen$: BehaviorSubject<boolean>;

  constructor() {}

  ngOnInit(): void {}
}
