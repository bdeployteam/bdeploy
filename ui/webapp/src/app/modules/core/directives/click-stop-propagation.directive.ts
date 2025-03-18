import { Directive, HostListener } from '@angular/core';

@Directive({ selector: '[appClickStopPropagation]' })
export class ClickStopPropagationDirective {
  @HostListener('click', ['$event'])
  public onClick(event: Event): void {
    event.stopPropagation();
  }
}
