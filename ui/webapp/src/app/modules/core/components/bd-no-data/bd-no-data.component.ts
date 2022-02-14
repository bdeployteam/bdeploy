import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-bd-no-data',
  templateUrl: './bd-no-data.component.html',
  styleUrls: ['./bd-no-data.component.css'],
})
export class BdNoDataComponent {
  @Input() header: string;
}
