import { Injectable } from '@angular/core';
import { ContentCompletion } from '../components/bd-content-assist-menu/bd-content-assist-menu.component';
import { getRecursivePrefix } from '../utils/completion.utils';

@Injectable({
  providedIn: 'root',
})
export class MonacoCompletionsService {
  private completions: ContentCompletion[];
  private recursivePrefixes: string[];

  public getCompletions(word: string, range: any): any[] {
    const result = [];

    if (!this.completions?.length) {
      return result;
    }

    this.completions.forEach((c) =>
      result.push({
        label: c.value,
        insertText: c.value,
        detail: c.description,
        range: range,
      }),
    );

    if (!this.recursivePrefixes?.length) {
      return result;
    }

    const recursivePrefix = getRecursivePrefix(word, '{{', this.recursivePrefixes);

    if (recursivePrefix === '{{') {
      return result;
    }

    this.completions.forEach((c) => {
      result.push({
        label: c.value.replace('{{', ''),
        insertText: c.value.replace('{{', ''),
        detail: c.description,
        range: {
          ...range,
          startColumn: range.startColumn + recursivePrefix.length,
        },
      });
    });

    return result;
  }

  public setCompletions(completions: ContentCompletion[], recursivePrefixes: string[]): void {
    this.completions = completions;
    this.recursivePrefixes = recursivePrefixes;
  }
}
