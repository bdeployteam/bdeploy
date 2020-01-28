import { TemplatePortal } from '@angular/cdk/portal';
import { Component, Input, OnInit, ViewContainerRef } from '@angular/core';
import { InstanceNotification, Severity } from '../instance-notifications/instance-notifications.component';

@Component({
  selector: 'app-instance-notifications-item',
  templateUrl: './instance-notifications-item.component.html',
  styleUrls: ['./instance-notifications-item.component.css']
})
export class InstanceNotificationsItemComponent implements OnInit {

  @Input()
  notification: InstanceNotification;

  templatePortal: TemplatePortal<any>;

  constructor(private viewContainerRef: ViewContainerRef) { }

  ngOnInit() {
    this.templatePortal = new TemplatePortal(this.notification.template, this.viewContainerRef);
  }

  isInfo() {
    return this.notification.severity === Severity.INFO;
  }

  isWarning() {
    return this.notification.severity === Severity.WARNING;
  }

  isError() {
    return this.notification.severity === Severity.ERROR;
  }

}
