import { Injectable } from '@angular/core';
import { ContentCompletion } from '../components/bd-content-assist-menu/bd-content-assist-menu.component';
import { getRecursivePrefix } from '../utils/completion.utils';
import { editor, languages, Uri, IRange} from 'monaco-editor';
import CompletionItem = languages.CompletionItem;

export interface GlobalMonacoModule {
  __providerRegistered: boolean;
  editor: typeof editor;
  languages: typeof languages;
  Uri: typeof Uri;
}

export interface WindowWithMonacoLoaded extends Window {
  monaco?: GlobalMonacoModule;
}

const kindByIcon = (globalMonaco: GlobalMonacoModule, icon: string) => {
  // see completion.utils.ts!
  switch (icon) {
    case 'data_object': // instance & system variable.
      return globalMonaco.languages.CompletionItemKind.Variable;
    case 'build': // process parameters
      return globalMonaco.languages.CompletionItemKind.Keyword;
    case 'folder': // deployment folders
      return globalMonaco.languages.CompletionItemKind.Folder;
    case 'dns': // host and environment
      return globalMonaco.languages.CompletionItemKind.User;
    case 'settings_system_daydream': // instance properties
      return globalMonaco.languages.CompletionItemKind.Class;
    case 'folder_special': // manifest reference
      return globalMonaco.languages.CompletionItemKind.Reference;
    case 'schedule': // delayed
      return globalMonaco.languages.CompletionItemKind.Operator;
    case 'devices_other': // os expansion
      return globalMonaco.languages.CompletionItemKind.Interface;
  }
  return globalMonaco.languages.CompletionItemKind.Constant; // should not happen
};

@Injectable({
  providedIn: 'root',
})
export class MonacoCompletionsService {
  private completions: ContentCompletion[];
  private recursivePrefixes: string[];

  public getCompletions(globalMonaco: GlobalMonacoModule, word: string, range: IRange): CompletionItem[] {
    const result: CompletionItem[] = [];

    if (!this.completions?.length) {
      return result;
    }

    this.completions.forEach((c) =>
      result.push({
        label: c.value,
        insertText: c.value,
        detail: c.description,
        range: range,
        kind: kindByIcon(globalMonaco, c.icon),
      } as CompletionItem),
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
        kind: kindByIcon(globalMonaco, c.icon),
      } as CompletionItem);
    });

    return result;
  }

  public setCompletions(completions: ContentCompletion[], recursivePrefixes: string[]): void {
    this.completions = completions;
    this.recursivePrefixes = recursivePrefixes;
  }
}
