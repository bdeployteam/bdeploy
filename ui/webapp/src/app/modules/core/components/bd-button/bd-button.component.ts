import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ThemePalette } from '@angular/material/core';
import { TooltipPosition } from '@angular/material/tooltip';
import { delayedFadeIn } from '../../animations/fades';
import { scaleWidthFromZero } from '../../animations/sizes';

@Component({
  selector: 'app-bd-button',
  templateUrl: './bd-button.component.html',
  styleUrls: ['./bd-button.component.css'],
  animations: [delayedFadeIn, scaleWidthFromZero],
})
export class BdButtonComponent implements OnInit {
  @Input() icon: string;
  @Input() text: string;
  @Input() tooltip: TooltipPosition;
  @Input() badge: number;
  @Input() badgeColor: ThemePalette = 'accent';
  @Input() collapsed = true;
  @Input() inverseColor = false;

  @Input() isToggle = false;
  @Input() toggle = false;
  @Output() toggleChange = new EventEmitter<boolean>();

  constructor() {}

  ngOnInit(): void {}

  onClick(event: MouseEvent): void {
    if (this.isToggle) {
      this.toggle = !this.toggle;
      this.toggleChange.emit(this.toggle);
      event.preventDefault();
    }
  }
}
