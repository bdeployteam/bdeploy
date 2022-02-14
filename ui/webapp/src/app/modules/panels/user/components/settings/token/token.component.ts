import { Component, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { UserInfo } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';

@Component({
  selector: 'app-token',
  templateUrl: './token.component.html',
  styleUrls: ['./token.component.css'],
})
export class TokenComponent implements OnInit {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ user$ = new BehaviorSubject<UserInfo>(null);
  /* template */ pack$ = new BehaviorSubject<string>('');
  /* template */ genFull = false;

  constructor(
    private authService: AuthenticationService,
    private snackbarService: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.authService.getUserInfo().subscribe((r) => {
      this.user$.next(r);
      this.regenPack();
    });
  }

  regenPack() {
    this.loading$.next(true);
    this.authService
      .getAuthPackForUser(this.genFull)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((r) => this.pack$.next(r));
  }

  /* template */ doCopy(value: string) {
    navigator.clipboard.writeText(value).then(
      () =>
        this.snackbarService.open('Copied to clipboard successfully', null, {
          duration: 1000,
        }),
      () =>
        this.snackbarService.open('Unable to write to clipboard.', null, {
          duration: 1000,
        })
    );
  }
}
