import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { InstanceDto } from '../models/gen.dtos';
import { LoggingService } from '../modules/core/services/logging.service';
import { MessageBoxMode } from '../modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from '../modules/shared/services/messagebox.service';
import { InstanceService } from '../services/instance.service';


@Component({
  selector: 'app-instance-card',
  templateUrl: './instance-card.component.html',
  styleUrls: ['./instance-card.component.css']
})
export class InstanceCardComponent implements OnInit {

  @Input() instanceDto: InstanceDto;
  @Input() instanceGroupName: string;
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
        title: 'Delete Instance ' + this.instanceDto.instanceConfiguration.name,
        message: 'Deleting an instance <b>cannot be undone</b>.',
        mode: MessageBoxMode.CONFIRM_WARNING
      })
      .subscribe(result => {
        if (result !== true) {
          return;
        }
        this.instanceService
          .deleteInstance(this.instanceGroupName, this.instanceDto.instanceConfiguration.uuid)
          .subscribe(
            r => {
              this.removeEvent.emit(true);
            }
          );
      });
  }
}
