import { Injectable } from '@angular/core';
import { ContentCompletion } from '../components/bd-content-assist-menu/bd-content-assist-menu.component';

@Injectable({
  providedIn: 'root',
})
export class MonacoCompletionsService {
  private completions: ContentCompletion[];

  public getCompletions(): ContentCompletion[] {
    return this.completions;
  }

  public setCompletions(completions: ContentCompletion[]): void {
    this.completions = completions;
  }
}
