import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material';

/**
 * Error Message
 */
export class ErrorMessage {
  constructor(private msg: string, private details: any) {}

  public getMessage(): string {
    return this.msg;
  }

  public getDetails(): any {
    return this.details;
  }
}

/**
 * LogLevel
 */
export enum LogLevel {
  ERROR,
  WARN,
  INFO,
  DEBUG,
  TRACE,
}

/**
 * Logger. Delegates to appenders and to AppValve if required
 */
export class Logger {
  public static ROOT_LOGGER_ID = '';

  private loggerPath: string;

  private children: Object = {}; // hashMap

  private loglevel: LogLevel;

  private appenders: Appender[] = [];

  constructor(private parent: Logger, private loggerId, private appValve: AppValve, private appender?: Appender) {
    this.loggerPath = (parent == null || parent.getId().length === 0 ? '' : parent.getId() + '.') + loggerId;
    if (appender != null) {
      this.appenders.push(appender);
    }
  }

  public getId(): string {
    return this.loggerId;
  }

  public getPath(): string {
    return this.loggerPath;
  }

  public findOrCreateLogger(id: string): Logger {
    const idx = id.indexOf('.');
    const subloggerId = idx === -1 ? id : id.substring(0, idx).trim();
    const remainingIds = idx === -1 ? '' : id.substr(idx + 1).trim();

    // ignore multiple dots (treat 'a..b...c' like 'a.b.c')
    if (subloggerId.length === 0) {
      return this;
    }

    let sublogger = this.children[subloggerId];
    if (sublogger == null) {
      sublogger = new Logger(this, subloggerId, this.appValve);
      this.children[subloggerId] = sublogger;
      return sublogger;
    } else {
      return sublogger.findOrCreateLogger(remainingIds);
    }
  }

  public setLogLevel(loglevel: LogLevel): void {
    this.loglevel = loglevel;
  }

  public getLogLevel(): LogLevel {
    return this.loglevel || (this.parent ? this.parent.getLogLevel() : LogLevel.INFO);
  }

  public error(msg: string|ErrorMessage): void {
    this.appValve.error(msg);
    this.log(LogLevel.ERROR, this, msg);
  }

  public warn(msg: string|ErrorMessage): void {
    this.log(LogLevel.WARN, this, msg);
  }

  public info(msg: string|ErrorMessage): void {
    this.log(LogLevel.INFO, this, msg);
  }

  public debug(msg: string|ErrorMessage): void {
    this.log(LogLevel.DEBUG, this, msg);
  }

  public trace(msg: string|ErrorMessage): void {
    this.log(LogLevel.TRACE, this, msg);
  }

  public message(msg: string|ErrorMessage): void {
    this.appValve.message(msg);
  }

  private log(messageLogLevel: LogLevel, logger: Logger, msg: string|ErrorMessage): void {
    if (messageLogLevel <= logger.getLogLevel() && this.appenders != null) {
      for (let i = 0; i < this.appenders.length; i++) {
        this.appenders[i].log(messageLogLevel, logger, msg);
      }
    }

    if (this.parent != null) {
      this.parent.log(messageLogLevel, logger, msg);
    }
  }
}

/**
 * Appender
 */
export interface Appender {
  setLogLevel(loglevel: LogLevel): void;

  getLogLevel(): LogLevel;

  log(loglevel: LogLevel, logger: Logger, msg: Object);
}

/**
 * Console Appender
 */
export class ConsoleAppender implements Appender {
  private loglevel: LogLevel;

  constructor(initialLogLevel: LogLevel) {
    this.loglevel = initialLogLevel;
  }

  public setLogLevel(loglevel: LogLevel): void {
    this.loglevel = loglevel;
  }

  public getLogLevel(): LogLevel {
    return this.loglevel;
  }

  public log(messageLogLevel: LogLevel, logger: Logger, details: Object): void {
    if (messageLogLevel <= this.loglevel) {
        const msg = this.fLogLevel(messageLogLevel) + ' ' + this.fNow() + ' ' + this.fLogger(logger) + '  ' + this.fMsg(details);
        if (messageLogLevel === LogLevel.ERROR) {
          console.error(msg);
        } else if (messageLogLevel === LogLevel.WARN) {
          console.warn(msg);
        } else {
          console.log(msg);
        }
    }
  }

  private fLogLevel(loglevel: LogLevel): string {
    const f = '     ';
    const s: string = LogLevel[loglevel];
    return s + f.substr(0, 5 - s.length);
  }

  private fNow(): string {
    const now = new Date();
    return (
      now.getFullYear() +
      '-' +
      this.f2d(now.getMonth() + 1) +
      '-' +
      this.f2d(now.getDate()) +
      ' ' +
      this.f2d(now.getHours()) +
      ':' +
      this.f2d(now.getMinutes()) +
      ':' +
      this.f2d(now.getSeconds()) +
      '.' +
      this.f3d(now.getMilliseconds())
    );
  }

  private fLogger(logger: Logger): string {
    const f = '                         '; // 25 spaces
    const path: string = logger.getPath();
    const s: string = path.substr(Math.max(0, path.length - 25));
    return '[' + f.substr(0, 25 - s.length) + s + ']';
  }

  private f2d(n: number): string {
    return n < 10 ? '0' + n : '' + n;
  }

  private f3d(n: number): string {
    return n < 100 ? '0' + this.f2d(n) : '' + n;
  }

  private fMsg(msg: Object): string {
    if (typeof msg === 'string') {
      return <string>msg;
    }
    if (msg instanceof ErrorMessage) {
      const errorMessage: ErrorMessage = <ErrorMessage>msg;
      const details: any = errorMessage.getDetails();
      if (details instanceof Error) {
        return errorMessage.getMessage() + ': ' + details.stack;
      } else if (details instanceof HttpErrorResponse) {
        return errorMessage.getMessage() + ' [' + details.status + ' - ' + details.statusText + ']: ' + details.message;
      } else {
        try {
          return errorMessage.getMessage() + ': ' + JSON.stringify(details);
        } catch (error) {
          // JSON.stringify fails for some types of object.
          return errorMessage.getMessage() + ': ' + details;
        }
      }
    }
    return 'UNKNOWN MESSAGE TYPE: ' + JSON.stringify(msg);
  }
}

/**
 * AppValve. Delegates back to the singleton logging service to display a message to the user.
 */
export class AppValve {
  constructor(private _loggingService: LoggingService) {}

  public error(msg: string|ErrorMessage): void {
    this._loggingService.guiError(msg);
  }

  public message(msg: string|ErrorMessage): void {
    this._loggingService.guiMessage(msg);
  }
}

/**
 * LoggingService. Provides loggers and functionality to display messages to the user.
 */
@Injectable()
export class LoggingService {
  private static CACHE_LENGTH = 10;

  private appValve: AppValve;
  private appender: Appender;
  private rootLogger: Logger;

  constructor(
    private snackbar: MatSnackBar
  ) {
    this.appValve = new AppValve(this);
    this.appender = new ConsoleAppender(LogLevel.TRACE);
    this.rootLogger = new Logger(null, Logger.ROOT_LOGGER_ID, this.appValve, this.appender);
  }

  public getLogger(id: string) {
    return id == null || id.trim().length === 0 ? this.rootLogger : this.rootLogger.findOrCreateLogger(id.trim());
  }

  public guiError(msg: string|ErrorMessage): void {
    this.snackbar.open('ERROR: ' + (msg instanceof ErrorMessage ? msg.getMessage() : msg), 'DISMISS', { panelClass: 'error-snackbar' });
  }

  public guiMessage(msg: string|ErrorMessage): void {
    this.snackbar.open((msg instanceof ErrorMessage ? msg.getMessage() : msg), 'DISMISS', { panelClass: 'error-snackbar' });
  }

  public dismissOpenMessage(): void {
    this.snackbar.dismiss();
  }

}
