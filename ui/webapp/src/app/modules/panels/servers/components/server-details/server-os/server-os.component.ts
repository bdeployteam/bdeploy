import { Component, Input, OnInit } from '@angular/core';
import { MinionRow } from '../server-details.component';

@Component({
  selector: 'app-server-os',
  templateUrl: './server-os.component.html',
  styleUrls: ['./server-os.component.css'],
})
export class ServerOsComponent implements OnInit {
  @Input() record: MinionRow;

  constructor() {}

  ngOnInit(): void {}
}
