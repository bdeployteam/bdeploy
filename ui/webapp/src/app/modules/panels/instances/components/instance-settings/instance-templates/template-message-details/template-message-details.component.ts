import { Component, Input, OnInit } from '@angular/core';
import { TemplateMessage } from '../instance-templates.component';

@Component({
  selector: 'app-template-message-details',
  templateUrl: './template-message-details.component.html',
  styleUrls: ['./template-message-details.component.css'],
})
export class TemplateMessageDetailsComponent implements OnInit {
  @Input() record: TemplateMessage;

  constructor() {}

  ngOnInit(): void {}
}
