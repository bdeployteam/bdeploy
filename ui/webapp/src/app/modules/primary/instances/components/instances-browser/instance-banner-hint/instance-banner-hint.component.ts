import { Component, Input, OnInit } from '@angular/core';
import { InstanceDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-instance-banner-hint',
  templateUrl: './instance-banner-hint.component.html',
  styleUrls: ['./instance-banner-hint.component.css'],
})
export class InstanceBannerHintComponent implements OnInit {
  @Input() record: InstanceDto;

  constructor() {}

  ngOnInit(): void {}
}
