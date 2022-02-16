import { Component, EventEmitter, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { debounceTime, Subscription } from 'rxjs';
import { ApplicationDto, ApplicationStartType } from 'src/app/models/gen.dtos';
import { ProcessEditService } from '../../../../services/process-edit.service';

@Component({
  selector: 'app-config-process-header',
  templateUrl: './config-process-header.component.html',
  styleUrls: ['./config-process-header.component.css'],
})
export class ConfigProcessHeaderComponent implements OnInit, OnDestroy {
  @ViewChild('form') public form: NgForm;
  @Output() checkIsInvalid = new EventEmitter<boolean>();

  /* template */ app: ApplicationDto;
  /* template */ startTypes: ApplicationStartType[];
  /* template */ startTypeLabels: string[];
  /* template */ hasPendingChanges: boolean = false;

  private subscription: Subscription;

  constructor(public edit: ProcessEditService) {}

  ngOnInit(): void {
    this.subscription = this.edit.application$.subscribe((application) => {
      this.app = application;
      this.startTypes = this.getStartTypes(this.app);
      this.startTypeLabels = this.getStartTypeLabels(this.app);
    });
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.statusChanges.pipe(debounceTime(100)).subscribe((status) => {
        this.checkIsInvalid.emit(status !== 'VALID');
      })
    );
  }

  private getStartTypes(app: ApplicationDto): ApplicationStartType[] {
    const supported = app?.descriptor?.processControl?.supportedStartTypes;
    if (!supported?.length || !!supported.find((s) => s === ApplicationStartType.INSTANCE)) {
      return [ApplicationStartType.INSTANCE, ApplicationStartType.MANUAL, ApplicationStartType.MANUAL_CONFIRM];
    } else if (!!supported.find((s) => s === ApplicationStartType.MANUAL)) {
      return [ApplicationStartType.MANUAL, ApplicationStartType.MANUAL_CONFIRM];
    } else {
      return supported;
    }
  }

  private getStartTypeLabels(app: ApplicationDto): string[] {
    return this.getStartTypes(app).map((t) => {
      switch (t) {
        case ApplicationStartType.INSTANCE:
          return 'Instance (Automatic)';
        case ApplicationStartType.MANUAL:
          return 'Manual';
        case ApplicationStartType.MANUAL_CONFIRM:
          return 'Manual (with confirmation)';
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
