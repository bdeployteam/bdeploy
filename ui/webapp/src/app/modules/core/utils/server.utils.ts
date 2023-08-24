import { HttpHeaders } from '@angular/common/http';
import { OperatorFunction } from 'rxjs';
import { delay, retryWhen, scan } from 'rxjs/operators';
import {
  NO_ERROR_HANDLING_HDR,
  NO_UNAUTH_DELAY_HDR,
} from 'src/app/models/consts';

export function suppressGlobalErrorHandling(p: HttpHeaders): HttpHeaders {
  return p.append(NO_ERROR_HANDLING_HDR, 'true');
}

export function suppressUnauthenticatedDelay(p: HttpHeaders): HttpHeaders {
  return p.append(NO_UNAUTH_DELAY_HDR, 'true');
}

/**
 * Retries to execute the source observable in case that an error happens
 */
export function retryWithDelay<T>(
  times = 60,
  delayInMs = 1000
): OperatorFunction<T, T> {
  return retryWhen((errors) =>
    errors.pipe(
      scan((count, err) => {
        if (count >= times) {
          throw err;
        }
        return count + 1;
      }, 0),
      delay(delayInMs)
    )
  );
}
