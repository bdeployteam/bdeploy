import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-attach-central',
  templateUrl: './attach-central.component.html',
  styleUrls: ['./attach-central.component.css']
})
export class AttachCentralComponent implements OnInit {

  constructor(public location: Location) { }

  ngOnInit() {
  }

}
