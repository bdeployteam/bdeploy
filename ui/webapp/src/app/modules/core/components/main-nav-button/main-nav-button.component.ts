import { Component, Input, OnInit } from '@angular/core';
import { delayedFadeIn, delayedFadeOut } from '../../animations/fades';
import { easeX } from '../../animations/positions';

@Component({
  selector: 'app-main-nav-button',
  templateUrl: './main-nav-button.component.html',
  styleUrls: ['./main-nav-button.component.css'],
  animations: [delayedFadeIn, delayedFadeOut, easeX],
})
export class MainNavButtonComponent implements OnInit {
  @Input() icon: string;
  @Input() text: string;
  @Input() collapsed: boolean;

  constructor() {}

  ngOnInit(): void {}
}
