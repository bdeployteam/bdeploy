import { defer, Observable, throwError } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';

/**
 * Creates an Observable that mirrors the source Observable and adds performance marks for:
 * - subscribe: when an Observer subscribes to the source Observable.
 * - next: when the source Observable emits a next notification.
 * - error: when the source Observable emits an error notification.
 * - complete: when the source Observer terminates on complete or error.
 *
 * It will also create a measurement to measure the time between subscribe and complete.
 * This measurement along with the rolling average for the same name will be logged to the console.
 *
 * Note that this only works on Observables which complete by design, thus it makes no sense on
 * (e.g.) BehaviorSubject which never completes.
 *
 * @param name The name for the marks and measurement.
 */
export const measure = function <T>(name: string) {
  const prefix = 'bdeploy:';
  const nativeWindow = window;
  const fullName = prefix + name;
  return (source: Observable<T>) => {
    if ('performance' in nativeWindow && nativeWindow.performance !== undefined) {
      return defer(() => {
        nativeWindow.performance.mark(`${fullName}:subscribe`);
        return source.pipe(
          catchError((error) => {
            nativeWindow.performance.mark(`${fullName}:error`);
            return throwError(() => error);
          }),
          finalize(() => {
            nativeWindow.performance.mark(`${fullName}:complete`);
            try {
              nativeWindow.performance.measure(`${fullName}`, `${fullName}:subscribe`, `${fullName}:complete`);
              logMeasurements(name, nativeWindow.performance.getEntriesByName(fullName, 'measure'));
            } catch (err) {
              console.warn(`Error while measuring ${fullName}.`, err);
            }
          })
        );
      });
    }
    return source;
  };
};

function logMeasurements(name: string, entries: PerformanceEntryList) {
  const last = entries.at(-1);
  const avg = entries.map((entry) => entry.duration).reduce((p, c) => p + c, 0) / entries.length;
  console.group(name);
  try {
    logTiming('Total Duration [ms]', last.duration);
    logTiming('Average Total [ms]', avg);
  } finally {
    console.groupEnd();
  }
}

function logTiming(label: string, duration: number) {
  if (duration > 300) {
    console.warn(label, duration);
  } else {
    console.log(label, duration);
  }
}
