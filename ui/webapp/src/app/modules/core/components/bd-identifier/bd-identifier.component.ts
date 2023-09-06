import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-bd-identifier',
  templateUrl: './bd-identifier.component.html',
})
export class BdIdentifierComponent {
  @Input() id: string;
}
