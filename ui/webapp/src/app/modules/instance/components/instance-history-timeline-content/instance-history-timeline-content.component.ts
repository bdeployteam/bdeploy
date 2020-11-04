import { Component, Input, OnInit } from '@angular/core';
import { HistoryEntryVersionDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-instance-history-timeline-content',
  templateUrl: './instance-history-timeline-content.component.html',
  styleUrls: ['./instance-history-timeline-content.component.css'],
})
export class InstanceHistoryTimelineContentComponent implements OnInit {
  @Input('history')
  public history: HistoryEntryVersionDto;

  constructor() {}

  ngOnInit(): void {}

  isEmpty(object: object) {
    return Object.keys(object).length == 0;
  }
}
