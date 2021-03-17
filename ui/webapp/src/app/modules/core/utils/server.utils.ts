import { HttpHeaders } from '@angular/common/http';
import { of, OperatorFunction } from 'rxjs';
import { catchError, delay, retryWhen, scan, takeWhile } from 'rxjs/operators';
import { NO_ERROR_HANDLING_HDR } from '../../../models/consts';

export function suppressGlobalErrorHandling(p: HttpHeaders): HttpHeaders {
  return p.append(NO_ERROR_HANDLING_HDR, 'true');
}

/**
 * Retries to execute the source observable in case that an error happens
 */
export function retryWithDelay<T>(times = 60, delayInMs = 1000, fallback = null): OperatorFunction<T, T> {
  return retryWhen((errors) =>
    errors.pipe(
      scan((acc) => acc + 1, 0),
      takeWhile((acc) => acc < times),
      delay(delayInMs),
      catchError((_) => {
        return of(fallback);
      })
    )
  );
}
