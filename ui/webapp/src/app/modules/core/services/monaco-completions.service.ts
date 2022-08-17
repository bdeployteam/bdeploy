import { Injectable } from '@angular/core';
import { ContentCompletion } from '../components/bd-content-assist-menu/bd-content-assist-menu.component';

@Injectable({
  providedIn: 'root',
})
export class MonacoCompletionsService {
  private completions: ContentCompletion[];

  getCompletions(): ContentCompletion[] {
    return this.completions;
  }

  setCompletions(completions: ContentCompletion[]): void {
    this.completions = completions;
  }
}
