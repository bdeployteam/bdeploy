import {
  AfterViewInit,
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
  inject,
} from '@angular/core';
import { SearchAddon } from '@xterm/addon-search';
import { WebglAddon } from '@xterm/addon-webgl';
import { IDisposable } from '@xterm/xterm';
import { NgTerminal, NgTerminalModule } from 'ng-terminal';
import { Observable, Subscription } from 'rxjs';
import { SearchService } from '../../services/search.service';

// TODO: Move to 'FunctionsUsingCSI' where possible.
// Sequences documentation: https://xtermjs.org/docs/api/vtfeatures/
const ESC = '\u001b'; // ESC
const SC: string = ESC + '7'; // Save Cursor
const RC: string = ESC + '8'; // Restore Cursor

const CSI: string = ESC + '['; // Control Sequence Introducer "ESC ["
const CNL: string = CSI + 'E'; // Cursor Next Line (col=0)
const EL_TO_EOL: string = CSI + '0K'; // clear from cursor to EOL

@Component({
  selector: 'app-bd-terminal',
  templateUrl: './bd-terminal.component.html',
  styleUrls: ['./bd-terminal.component.css'],
  imports: [NgTerminalModule],
})
export class BdTerminalComponent implements AfterViewInit, OnInit, OnDestroy {
  private readonly searchService = inject(SearchService);

  @Input() content$: Observable<string>;
  @Input() allowInput = false;
  @Output() userInput = new EventEmitter<string>();

  @ViewChild('term') private readonly term: NgTerminal;

  private stdinResize: IDisposable;
  private stdinSubscription: Subscription;

  private stdinBuffer = '';
  private stdinBufferCursorPos = 0;
  private stdinStartX: number;

  private readonly searchAddon = new SearchAddon();
  private webglAddon = new WebglAddon();
  private searchSubscription: Subscription;

  ngOnInit() {
    this.searchSubscription = this.searchService.register(this);
  }

  ngAfterViewInit() {
    this.term.underlying.attachCustomKeyEventHandler(
      (event: KeyboardEvent) =>
        // prevent default handling of Ctrl-C/Ctrl-X (otherwise it resets the selections
        // and default Ctrl-C/X has nothing to copy) and Ctrl-V
        !event.ctrlKey || 'cxv'.includes(event.key.toLowerCase())
    );

    this.term.underlying.options.fontSize = 12;

    this.content$.subscribe((content) => {
      if (this.allowInput) {
        this.clearInput();
      }
      this.term.write(this.prepareLinebreaks(content));
      if (this.allowInput) {
        this.term.write(SC);
        this.updateInput();
      }
    });

    this.setupStdin();

    this.term.underlying.loadAddon(this.searchAddon);
    this.term.underlying.loadAddon(this.webglAddon);
    this.webglAddon.onContextLoss(() => this.reloadWebgl());
  }

  private reloadWebgl() {
    this.webglAddon.dispose();
    this.webglAddon = new WebglAddon();
    this.term.underlying.loadAddon(this.webglAddon);
    this.webglAddon.onContextLoss(() => this.reloadWebgl());
  }

  ngOnDestroy(): void {
    if (this.allowInput) {
      this.stdinSubscription?.unsubscribe();
      this.stdinSubscription = null;
      this.stdinResize.dispose();
      this.stdinResize = null;
    }
    this.searchSubscription?.unsubscribe();
  }

  bdOnSearch(search: string): void {
    this.searchAddon.findNext(search, {
      decorations: {
        matchBackground: '#ebe41c', // yellow
        matchOverviewRuler: '#ebe41c',
        activeMatchColorOverviewRuler: '#ebe41c',
        activeMatchBackground: '#ebe41c',
      },
    });
  }

  /** Make sure that *all* linebreaks are \r\n */
  private prepareLinebreaks(s: string): string {
    if (!s) {
      return '';
    }
    return s.replace(/\r\n/g, '\n').replace(/\n/g, '\r\n');
  }

  /** Retrieve the current amount of columns in the terminal */
  private getTermCols(): number {
    return this.term.underlying ? this.term.underlying.cols : undefined;
  }

  /** Retrieve the current X position of the cursor */
  private getCursorX(): number {
    return this.term.underlying ? this.term.underlying.buffer.active.cursorX : undefined;
  }

  /** Wires up user input possibilities if they are enabled. */
  private setupStdin() {
    if (this.allowInput) {
      this.stdinResize = this.term.underlying.onResize(() => {
        setTimeout(() => {
          this.clearInput();
          this.updateInput();
        }, 0);
      });

      // TODO: migrate to keyEventInput - need a solution for pasting, everything should be straight forward.
      this.stdinSubscription = this.term.keyInput.subscribe((input) => {
        // store initial position on first keystroke after terminal output
        if (!this.stdinStartX) {
          this.stdinStartX = this.getCursorX();
        }

        // keyboard input usually is a single charactor or CSI sequence, but clipboard content is a string
        let idx = 0;
        while (idx < input.length) {
          if (input[idx] === '\r') {
            idx++;
            this.sendStdin();
          } else if (input.codePointAt(idx) === 0x7f) {
            idx++;
            if (this.stdinBufferCursorPos > 0) {
              this.clearInput();
              this.stdinBuffer =
                this.stdinBuffer.substring(0, this.stdinBufferCursorPos - 1) +
                this.stdinBuffer.substring(this.stdinBufferCursorPos);
              this.stdinBufferCursorPos--;
              this.updateInput();
            }
          } else {
            this.clearInput();
            if (input.codePointAt(idx) >= 32) {
              this.stdinBuffer = [
                this.stdinBuffer.slice(0, this.stdinBufferCursorPos),
                input[idx],
                this.stdinBuffer.slice(this.stdinBufferCursorPos),
              ].join('');
              this.stdinBufferCursorPos++;
              idx++;
            } else if (input.substr(idx).startsWith(CSI)) {
              idx += CSI.length;
              if (idx < input.length) {
                switch (input[idx]) {
                  case 'C': // cursor right
                    this.stdinBufferCursorPos =
                      this.stdinBufferCursorPos + (this.stdinBuffer.length > this.stdinBufferCursorPos ? 1 : 0);
                    break;
                  case 'D': // cursor left
                    this.stdinBufferCursorPos = this.stdinBufferCursorPos - (this.stdinBufferCursorPos > 0 ? 1 : 0);
                    break;
                  case 'H': // pos 1
                    this.stdinBufferCursorPos = 0;
                    break;
                  case 'F': // end
                    this.stdinBufferCursorPos = this.stdinBuffer.length;
                    break;
                }
                idx++;
              }
            } else {
              // consume and ignore input character
              idx++;
            }
            this.updateInput();
          }
        }
      });
    }
  }

  /** Emits the current input and clears all buffers */
  private sendStdin() {
    this.clearInput();
    this.userInput.emit(this.stdinBuffer);
    this.clearBuffer();
  }

  /** Clears the input buffers */
  private clearBuffer(): void {
    this.stdinBuffer = '';
    this.stdinBufferCursorPos = 0;
    this.stdinStartX = null;
  }

  /** Clears the currently *visible* input, but not the buffers. Restores the cursor position as if no user input ever happened */
  private clearInput(): void {
    this.term.write(RC); // restore cursor
    this.term.write(EL_TO_EOL); // clear from cursor to EOL
    for (let i = 0; i < this.getInputLineCount() - 1; i++) {
      this.term.write(CNL); // next line
      this.term.write(EL_TO_EOL); // clear whole line
    }
    this.term.write(RC); // restore cursor
  }

  /** Re-writes the user input buffers to the terminal */
  private updateInput(): void {
    this.term.write(this.stdinBuffer);
    this.term.write(RC); // restore cursor
    this.term.write(this.stdinBuffer.substr(0, this.stdinBufferCursorPos));
  }

  /** Retrieves the current amoun of lines taken up by user input */
  private getInputLineCount() {
    return this.stdinBuffer ? Math.ceil((this.stdinStartX + this.stdinBuffer.length) / this.getTermCols()) : 1;
  }

  /** Clears all existing content in the terminal */
  public clear() {
    this.term.underlying.reset();
  }
}
