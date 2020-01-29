import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { InstanceDto } from '../../../../models/gen.dtos';
import { LoggingService } from '../../../core/services/logging.service';
import { MessageBoxMode } from '../../../shared/components/messagebox/messagebox.component';
import { MessageboxService } from '../../../shared/services/messagebox.service';
import { InstanceService } from '../../services/instance.service';


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
    public authService: AuthenticationService,
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
