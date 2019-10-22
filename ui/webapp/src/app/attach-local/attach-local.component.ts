import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-attach-local',
  templateUrl: './attach-local.component.html',
  styleUrls: ['./attach-local.component.css']
})
export class AttachLocalComponent implements OnInit {

  instanceGroupName: string = this.route.snapshot.paramMap.get('group');

  constructor(public location: Location, private route: ActivatedRoute) { }

  ngOnInit() {
  }

}
