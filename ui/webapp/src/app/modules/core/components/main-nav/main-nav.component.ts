import { Component, inject, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { combineLatest, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ActionsService } from '../../services/actions.service';
import { AuthenticationService } from '../../services/authentication.service';
import { ConfigService } from '../../services/config.service';
import { ObjectChangesService } from '../../services/object-changes.service';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-main-nav',
  templateUrl: './main-nav.component.html',
  styleUrls: ['./main-nav.component.css'],
})
export class MainNavComponent implements OnInit {
  private readonly authService = inject(AuthenticationService);
  private readonly changes = inject(ObjectChangesService);
  private readonly config = inject(ConfigService);
  private readonly snackbar = inject(MatSnackBar);
  protected readonly themeService = inject(ThemeService);
  protected readonly actions = inject(ActionsService);

  isAuth$: Observable<boolean> = this.authService.getTokenSubject().pipe(map((s) => s !== null));

  ngOnInit() {
    // TODO: move this code to objectchangesservice?
    combineLatest([this.changes.errorCount$, this.config.offline$]).subscribe(([count, offline]) => {
      // in case we exceed a certain threshold, we will show a warning to the user. the error count is reset, and we start over again in case the user dismisses this message.
      if (count === 3 && !offline) {
        // we check for the exact number to avout repeated messages.
        // this is a number that should not happen in a typical session, *except* when there is a systemic problem with websockets. also
        // this number of errors should occur "rather" quickly in case there is a websocket issue.

        this.snackbar
          .open(
            'There seems to be an issue with WebSockets on your System. Communication with the server is restricted. New data will not be received without refresh.',
            'ACKNOWLEDGE',
            { panelClass: 'error-snackbar' },
          )
          .afterDismissed()
          .subscribe(() => {
            // reset so we start counting and waiting for errors over again.
            this.changes.errorCount$.next(0);
          });
      }
    });
  }
}
