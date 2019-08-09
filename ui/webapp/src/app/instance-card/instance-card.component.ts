import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MessageBoxMode } from '../messagebox/messagebox.component';
import { InstanceConfiguration, ProductDto } from '../models/gen.dtos';
import { InstanceService } from '../services/instance.service';
import { LoggingService } from '../services/logging.service';
import { MessageboxService } from '../services/messagebox.service';


@Component({
  selector: 'app-instance-card',
  templateUrl: './instance-card.component.html',
  styleUrls: ['./instance-card.component.css']
})
export class InstanceCardComponent implements OnInit {

  @Input() instance: InstanceConfiguration;
  @Input() instanceGroupName: string;
  @Input() product: ProductDto;
  @Output() removeEvent = new EventEmitter<boolean>();

  private log = this.loggingService.getLogger('InstanceCardComponent');

  constructor(
    private loggingService: LoggingService,
    private instanceService: InstanceService,
    private mbService: MessageboxService,
  ) {}

  ngOnInit() {
  }

  delete(): void {
    this.mbService
      .open({
        title: 'Delete Instance ' + this.instance.name,
        message: 'Deleting an instance <b>cannot be undone</b>.',
        mode: MessageBoxMode.CONFIRM_WARNING
      })
      .subscribe(result => {
        if (result !== true) {
          return;
        }
        this.instanceService
          .deleteInstance(this.instanceGroupName, this.instance.uuid)
          .subscribe(
            r => {
              this.removeEvent.emit(true);
            }
          );
      });
  }
}
