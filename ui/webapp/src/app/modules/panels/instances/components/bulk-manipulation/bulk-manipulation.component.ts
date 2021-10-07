import { Component, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { InstanceBulkService } from '../../services/instance-bulk.service';

@Component({
  selector: 'app-bulk-manipulation',
  templateUrl: './bulk-manipulation.component.html',
  styleUrls: ['./bulk-manipulation.component.css'],
})
export class BulkManipulationComponent implements OnInit {
  /* template */ starting$ = new BehaviorSubject<boolean>(false);
  /* template */ stopping$ = new BehaviorSubject<boolean>(false);
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);
  /* template */ installing$ = new BehaviorSubject<boolean>(false);
  /* template */ activating$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(public bulk: InstanceBulkService) {}

  ngOnInit(): void {}

  /* template */ onStart() {
    this.starting$.next(true);
    this.bulk
      .start()
      .pipe(finalize(() => this.starting$.next(false)))
      .subscribe();
  }

  /* template */ onStop() {
    this.stopping$.next(true);
    this.bulk
      .stop()
      .pipe(finalize(() => this.stopping$.next(false)))
      .subscribe();
  }

  /* template */ onDelete() {
    this.dialog
      .confirm(
        `Delete ${this.bulk.selection$.value.length} instances?`,
        `This will delete <strong>${this.bulk.selection$.value.length}</strong> instances. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
        'warning',
        'I UNDERSTAND',
        null
      )
      .subscribe((r) => {
        if (!r) {
          return;
        }

        this.deleting$.next(true);
        this.bulk
          .delete()
          .pipe(finalize(() => this.deleting$.next(false)))
          .subscribe();
      });
  }

  /* template */ isAllSameProduct() {
    return this.bulk.selection$.value.every((i) => !!i.productDto?.key?.name && i.productDto.key.name === this.bulk.selection$.value[0].productDto.key.name);
  }

  /* template */ onInstall() {
    this.installing$.next(true);
    this.bulk
      .install()
      .pipe(finalize(() => this.installing$.next(false)))
      .subscribe();
  }

  /* template */ onActivate() {
    this.dialog
      .confirm(
        'Activate',
        'This will activate the latest versions of each selected instance. Are you sure?',
        'warning',
        'I UNDERSTAND',
        'Confirm using I UNDERSTAND'
      )
      .subscribe((r) => {
        if (!r) return;

        this.activating$.next(true);
        this.bulk
          .activate()
          .pipe(finalize(() => this.activating$.next(false)))
          .subscribe();
      });
  }
}
