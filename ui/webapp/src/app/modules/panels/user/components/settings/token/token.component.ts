import { Component, OnInit, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { UserInfo } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { BdDialogComponent } from '../../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormToggleComponent } from '../../../../../core/components/bd-form-toggle/bd-form-toggle.component';
import { FormsModule } from '@angular/forms';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-token',
    templateUrl: './token.component.html',
    styleUrls: ['./token.component.css'],
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdFormToggleComponent, FormsModule, MatFormField, MatLabel, MatInput, BdButtonComponent, AsyncPipe]
})
export class TokenComponent implements OnInit {
  private readonly authService = inject(AuthenticationService);
  private readonly snackbarService = inject(MatSnackBar);

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected user$ = new BehaviorSubject<UserInfo>(null);
  protected pack$ = new BehaviorSubject<string>('');
  protected genFull = true;

  ngOnInit(): void {
    this.authService.getUserInfo().subscribe((r) => {
      this.user$.next(r);
      this.regenPack();
    });
  }

  protected regenPack() {
    this.loading$.next(true);
    this.authService
      .getAuthPackForCurrentUser(this.genFull)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((r) => this.pack$.next(r));
  }

  protected doCopy(value: string) {
    navigator.clipboard.writeText(value).then(
      () =>
        this.snackbarService.open('Copied to clipboard successfully', null, {
          duration: 1000,
        }),
      () =>
        this.snackbarService.open('Unable to write to clipboard.', null, {
          duration: 1000,
        }),
    );
  }
}
