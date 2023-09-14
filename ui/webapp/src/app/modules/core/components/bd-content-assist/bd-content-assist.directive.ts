import { Directive, ElementRef, HostListener, Input, OnInit, inject } from '@angular/core';
import { BdContentAssistMenuComponent } from '../bd-content-assist-menu/bd-content-assist-menu.component';

enum Keys {
  TAB = 9,
  ENTER = 13,
  ESC = 27,
  UP = 38,
  DOWN = 40,
}

class WordInfo {
  word: string;
  begin: number;
  selection: number;
  end: number;
}

@Directive({
  selector: 'input[appBdContentAssist]',
  exportAs: 'appBdContentAssist',
})
export class BdContentAssistDirective implements OnInit {
  private elementRef = inject(ElementRef);

  @Input() appBdContentAssist: BdContentAssistMenuComponent;

  ngOnInit(): void {
    this.appBdContentAssist.onClickSelect = (s) => {
      this.replaceWord(s);
      setTimeout(() => this.elementRef.nativeElement.focus());
    };
  }

  @HostListener('focus') onFocus() {
    this.appBdContentAssist.show(this.getWordInfo().word);
  }

  @HostListener('blur') onBlur() {
    this.appBdContentAssist.hide();
  }

  @HostListener('keydown', ['$event']) onKeydown(event: KeyboardEvent) {
    if (!this.appBdContentAssist.isVisible()) {
      return;
    }

    if (event.key === 'ArrowDown') {
      this.appBdContentAssist.next();
      event.preventDefault();
    } else if (event.key === 'ArrowUp') {
      this.appBdContentAssist.previous();
      event.preventDefault();
    } else if (event.key === 'Enter' || event.key === 'Tab') {
      this.replaceWord(this.appBdContentAssist.select());
      event.preventDefault();
    } else if (event.key === 'Escape') {
      this.appBdContentAssist.hide();
      event.preventDefault();
    }
  }

  @HostListener('keyup', ['$event']) onKeyup(event: KeyboardEvent) {
    if (
      event.key === 'ArrowDown' ||
      event.key === 'ArrowUp' ||
      event.key === 'Enter' ||
      event.key === 'Tab' ||
      event.key === 'Escape'
    ) {
      return;
    }

    // don't update on control keys (arrow, enter, etc.)
    this.appBdContentAssist.show(this.getWordInfo().word);
  }

  private getWordInfo(): WordInfo {
    const input = this.elementRef.nativeElement as HTMLInputElement;
    const value = input.value;
    const cursor = input.selectionStart;

    // search from the cursor backwards for a whitespace or the beginning of the string.
    let searchString = value;
    if (cursor > 0 && cursor < value.length) {
      searchString = value.substring(0, cursor);
    }

    // we want to start completion on double curly braces.
    let wordBegin = searchString.lastIndexOf('{{');
    if (wordBegin < 0 || wordBegin > value.length) {
      wordBegin = 0;
    }

    return {
      word: searchString.substring(wordBegin),
      begin: wordBegin,
      selection: input.selectionEnd,
      end: cursor,
    };
  }

  private replaceWord(val: string) {
    if (!val) {
      return;
    }

    const input = this.elementRef.nativeElement as HTMLInputElement;
    const wordInfo = this.getWordInfo();
    input.value =
      input.value.substring(0, wordInfo.begin) +
      val +
      input.value.substring(wordInfo.selection !== wordInfo.end ? wordInfo.selection : wordInfo.end);

    // required to re-run validation on the input.
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }
}
