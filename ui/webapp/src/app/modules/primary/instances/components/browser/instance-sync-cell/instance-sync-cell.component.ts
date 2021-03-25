import { Component, Input, OnInit } from '@angular/core';
import { InstanceDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-instance-sync-cell',
  templateUrl: './instance-sync-cell.component.html',
  styleUrls: ['./instance-sync-cell.component.css'],
})
export class InstanceSyncCellComponent implements OnInit {
  @Input() record: InstanceDto;

  constructor() {}

  ngOnInit(): void {}
}
