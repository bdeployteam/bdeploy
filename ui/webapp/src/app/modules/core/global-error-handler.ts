import { LocationStrategy } from '@angular/common';
import { ErrorHandler, Injectable, Injector } from '@angular/core';
import { ErrorMessage, Logger, LoggingService } from './services/logging.service';

@Injectable()
export class GlobalErrorHandler implements ErrorHandler {

  constructor(private injector: Injector) { }

  handleError(error: any): void {

    const location: LocationStrategy = this.injector.get(LocationStrategy);
    const log: Logger = this.injector.get(LoggingService).getLogger('GlobalErrorHandler');

    const message = error.message ? error.message : error.toString();

    if (error instanceof Error) {
      log.error(new ErrorMessage('UNHANDLED ERROR', error));
    } else {
      log.error('UNHANDLED ERROR: ' + message + ' (path="' + location.path(true) + '")');
    }
  }
}
