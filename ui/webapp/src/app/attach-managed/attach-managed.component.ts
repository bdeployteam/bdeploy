import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatStep, MatStepper } from '@angular/material';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AttachCentralComponent } from '../attach-central/attach-central.component';
import { AttachIdentDto } from '../models/gen.dtos';
import { ConfigService } from '../services/config.service';
import { DownloadService } from '../services/download.service';
import { ErrorMessage } from '../services/logging.service';
import { ManagedServersService } from '../services/managed-servers.service';

@Component({
  selector: 'app-attach-managed',
  templateUrl: './attach-managed.component.html',
  styleUrls: ['./attach-managed.component.css'],
})
export class AttachManagedComponent implements OnInit {
  instanceGroupName: string = this.route.snapshot.paramMap.get('group');
  attachPayload: AttachIdentDto;
  infoGroup: FormGroup;
  attachSuccess = false;
  attachError: ErrorMessage;
  centralIdent: string;

  @ViewChild(MatStepper, { static: true })
  stepper: MatStepper;

  @ViewChild('doneStep', { static: true })
  doneStep: MatStep;

  @ViewChild('manualStep', { static: true })
  manualStep: MatStep;

  constructor(
    public location: Location,
    private route: ActivatedRoute,
    private fb: FormBuilder,
    private config: ConfigService,
    private dlService: DownloadService,
    private managedServers: ManagedServersService,
  ) {}

  ngOnInit() {
    this.infoGroup = this.fb.group({
      name: ['', Validators.required],
      desc: ['', Validators.required],
      uri: ['', Validators.required],
    });
  }

  onDrop($event: DragEvent) {
    $event.preventDefault();

    if ($event.dataTransfer.files.length > 0) {
      const reader = new FileReader();
      reader.onload = e => (this.attachPayload = JSON.parse(reader.result.toString()));
      reader.readAsText($event.dataTransfer.files[0]);
    } else if ($event.dataTransfer.types.includes(AttachCentralComponent.ATTACH_MIME_TYPE)) {
      this.attachPayload = JSON.parse($event.dataTransfer.getData(AttachCentralComponent.ATTACH_MIME_TYPE));
    }
  }

  onOver($event: DragEvent) {
    // need to cancel the event and return false to ALLOW drop.
    if ($event.preventDefault) {
      $event.preventDefault();
    }

    return false;
  }

  get serverNameControl() {
    return this.infoGroup.get('name');
  }

  get serverUriControl() {
    return this.infoGroup.get('uri');
  }

  get serverDescControl() {
    return this.infoGroup.get('desc');
  }

  updateFormDefaults() {
    if (!this.attachPayload) {
      return;
    }
    if (!this.serverNameControl.value) {
      this.serverNameControl.setValue(this.attachPayload.name);
    }
    if (!this.serverUriControl.value) {
      this.serverUriControl.setValue(this.attachPayload.uri);
    }
  }

  autoAddServer() {
    const payload = this.createIdent();
    this.managedServers
      .tryAutoAttach(this.instanceGroupName, payload)
      .pipe(
        catchError(e => {
          const error = new ErrorMessage('Cannot automatically attach to managed server', e);
          return of(error);
        }),
      )
      .subscribe(r => {
        if (r instanceof ErrorMessage) {
          this.attachError = r;
        } else {
          this.attachSuccess = true;
          this.stepper.selected = this.doneStep;
        }
      });
  }

  manualAddServer() {
    const payload = this.createIdent();

    this.managedServers.manualAttach(this.instanceGroupName, payload).subscribe(r => {
      this.stepper.selected = this.doneStep;
    });
  }

  private createIdent(): AttachIdentDto {
    return {
      name: this.serverNameControl.value,
      description: this.serverDescControl.value,
      uri: this.serverUriControl.value,
      auth: this.attachPayload.auth,
      lastSync: 0,
    };
  }

  getErrorMessage() {
    if (this.attachError && this.attachError.getDetails()) {
      const details = this.attachError.getDetails();
      if (details instanceof Error) {
        return details.stack;
      } else if (details instanceof HttpErrorResponse) {
        return details.statusText.replace(new RegExp(' //', 'g'), '<br/>');
      }
      return JSON.stringify(details);
    }
  }

  onStepChange($event: StepperSelectionEvent) {
    if ($event.selectedStep === this.manualStep) {
      this.loadCentralIdent();
    }
  }

  loadCentralIdent() {
    this.managedServers.getCentralIdent(this.instanceGroupName, this.createIdent()).subscribe(r => {
      this.centralIdent = r;
    });
  }

  onDragStart($event) {
    $event.dataTransfer.effectAllowed = 'link';
    $event.dataTransfer.setData(AttachCentralComponent.ATTACH_MIME_TYPE, this.centralIdent);
  }

  downloadManualJson() {
    this.dlService.downloadBlob('central-' + this.serverNameControl.value + '.txt', new Blob([this.centralIdent], {type: 'text/plain'}));
  }
}
