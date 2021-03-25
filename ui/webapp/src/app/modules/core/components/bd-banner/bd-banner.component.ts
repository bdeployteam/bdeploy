import { Component, Input, OnInit } from '@angular/core';
import { format } from 'date-fns';
import { InstanceBannerRecord } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-bd-banner',
  templateUrl: './bd-banner.component.html',
  styleUrls: ['./bd-banner.component.css'],
})
export class BdBannerComponent implements OnInit {
  @Input() banner: InstanceBannerRecord;

  constructor() {}

  ngOnInit(): void {
    console.log(this.banner);
  }

  /* template */ format(time: number) {
    return format(time, 'dd.MM.yyyy HH:mm:ss');
  }
}
