import { Component, OnInit } from '@angular/core';
import { InstancesService } from '../../services/instances.service';

@Component({
  selector: 'app-history',
  templateUrl: './history.component.html',
  styleUrls: ['./history.component.css'],
})
export class HistoryComponent implements OnInit {
  constructor(public instances: InstancesService) {}

  ngOnInit(): void {}
}
