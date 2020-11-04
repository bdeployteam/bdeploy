import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatStep, MatStepper } from '@angular/material/stepper';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { RoutingHistoryService } from 'src/app/modules/core/services/routing-history.service';
import { ServerValidators } from 'src/app/modules/shared/validators/server.validator';
import { ManagedMasterDto } from '../../../../models/gen.dtos';
import { ErrorMessage } from '../../../core/services/logging.service';
import { DownloadService } from '../../../shared/services/download.service';
import { ManagedServersService } from '../../services/managed-servers.service';
import { AttachCentralComponent } from '../attach-central/attach-central.component';

@Component({
  selector: 'app-attach-managed',
  templateUrl: './attach-managed.component.html',
  styleUrls: ['./attach-managed.component.css'],
})
export class AttachManagedComponent implements OnInit {
  instanceGroupName: string = this.route.snapshot.paramMap.get('group');
  attachPayload: ManagedMasterDto;
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
    private dlService: DownloadService,
    private managedServers: ManagedServersService,
    public routingHistoryService: RoutingHistoryService
  ) {}

  ngOnInit() {
    this.infoGroup = this.fb.group({
      name: ['', Validators.required],
      desc: ['', Validators.required],
      uri: ['', [Validators.required, ServerValidators.serverApiUrl]],
    });
  }

  onDrop($event: DragEvent) {
    $event.preventDefault();

    if ($event.dataTransfer.files.length > 0) {
      this.readFile($event.dataTransfer.files[0]);
    } else if ($event.dataTransfer.types.includes(AttachCentralComponent.ATTACH_MIME_TYPE)) {
      this.attachPayload = JSON.parse($event.dataTransfer.getData(AttachCentralComponent.ATTACH_MIME_TYPE));
    }
  }

  private readFile(file: File) {
    const reader = new FileReader();
    reader.onload = (e) => {
      this.attachPayload = JSON.parse(reader.result.toString());
    };
    reader.readAsText(file);
  }

  onUpload($event) {
    if ($event.target.files && $event.target.files.length > 0) {
      this.readFile($event.target.files[0]);
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
      this.serverNameControl.setValue(this.attachPayload.hostName);
    }
    this.serverNameControl.markAsTouched();
    if (!this.serverDescControl.value) {
      this.serverDescControl.setValue(this.attachPayload.description);
    }
    this.serverDescControl.markAsTouched();
    if (!this.serverUriControl.value) {
      this.serverUriControl.setValue(this.attachPayload.uri);
    }
    this.serverUriControl.markAsTouched();
  }

  autoAddServer() {
    const payload = this.createDto();
    this.managedServers
      .tryAutoAttach(this.instanceGroupName, payload)
      .pipe(
        catchError((e) => {
          const error = new ErrorMessage('Cannot automatically attach to managed server', e);
          return of(error);
        })
      )
      .subscribe((r) => {
        if (r instanceof ErrorMessage) {
          this.attachError = r;
        } else {
          this.attachSuccess = true;
          this.stepper.selected = this.doneStep;
        }
      });
  }

  manualAddServer() {
    const payload = this.createDto();

    this.managedServers.manualAttach(this.instanceGroupName, payload).subscribe((r) => {
      this.stepper.selected = this.doneStep;
    });
  }

  private createDto(): ManagedMasterDto {
    return {
      hostName: this.serverNameControl.value,
      description: this.serverDescControl.value,
      uri: this.serverUriControl.value,
      auth: this.attachPayload.auth,
      minions: this.attachPayload.minions,
      lastSync: 0,
      update: null,
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
    this.managedServers.getCentralIdent(this.instanceGroupName, this.createDto()).subscribe((r) => {
      this.centralIdent = r;
    });
  }

  onDragStart($event) {
    $event.dataTransfer.effectAllowed = 'link';
    $event.dataTransfer.setData(AttachCentralComponent.ATTACH_MIME_TYPE, this.centralIdent);
  }

  downloadManualJson() {
    this.dlService.downloadBlob(
      'central-' + this.serverNameControl.value + '.txt',
      new Blob([this.centralIdent], { type: 'text/plain' })
    );
  }
}
