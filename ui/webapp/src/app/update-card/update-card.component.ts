import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { GroupedKeys } from '../update-browser/update-browser.component';

@Component({
  selector: 'app-update-card',
  templateUrl: './update-card.component.html',
  styleUrls: ['./update-card.component.css']
})
export class UpdateCardComponent implements OnInit {

  @Input() public version: GroupedKeys;
  @Input() public allowUpdate = true;
  @Output() public update = new EventEmitter<GroupedKeys>();
  @Output() public delete = new EventEmitter<GroupedKeys>();

  constructor() { }

  ngOnInit() {
  }

}
