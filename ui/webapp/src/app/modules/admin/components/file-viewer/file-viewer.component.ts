import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, ViewChild } from '@angular/core';
import { FormBuilder } from '@angular/forms';
import { NgTerminal } from 'ng-terminal';
import { Observable, Subscription } from 'rxjs';
import { IDisposable } from 'xterm';
import { RemoteDirectoryEntry, StringEntryChunkDto } from '../../../../models/gen.dtos';

const MAX_TAIL = 512 * 1024; // 512KB max initial fetch.

@Component({
  selector: 'app-file-viewer',
  templateUrl: './file-viewer.component.html',
  styleUrls: ['./file-viewer.component.css'],
})
export class FileViewerComponent implements OnInit, AfterViewInit, OnChanges, OnDestroy {
  // Sequences documentation: https://xtermjs.org/docs/api/vtfeatures/
  static ESC = '\u001b'; // ESC
  static SC: string = FileViewerComponent.ESC + '7'; // Save Cursor
  static RC: string = FileViewerComponent.ESC + '8'; // Restore Cursor

  static CSI: string = FileViewerComponent.ESC + '['; // Control Sequence Introducer "ESC ["
  static CNL: string = FileViewerComponent.CSI + 'E'; // Cursor Next Line (col=0)
  static CPL: string = FileViewerComponent.CSI + 'F'; // Cursor Previous Line (col=0)
  static EL_TO_EOL: string = FileViewerComponent.CSI + '0K'; // clear from cursor to EOL
  static EL_ALL: string = FileViewerComponent.CSI + '2K'; // clear whole line

  @Input() title: string;
  @Input() initialEntry: () => Observable<RemoteDirectoryEntry>;
  @Input() contentFetcher: (offset: number, length: number) => Observable<StringEntryChunkDto>;
  @Input() contentDownloader: () => void;
  @Input() follow = false;

  @Output() closeEvent = new EventEmitter<void>();

  @Input() supportsStdin = false;
  @Input() hasStdin = false;
  @Output() inputEvent = new EventEmitter<string>();

  @ViewChild('term', { static: true }) term: NgTerminal;
  get termCols() {
    return this.term.underlying ? this.term.underlying.cols : undefined;
  }
  get cursorX() {
    return this.term.underlying ? this.term.underlying.buffer.active.cursorX : undefined;
  }
  content = '';
  private timer;
  private offset = 0;

  private stdinSubscription: Subscription;
  private resizeDisposable: IDisposable;
  buffer = '';
  bufferCursorPos = 0;
  initialCursorX: number; // cursor posX after terminal output (input field origin X)
  inputLineHeight_sav: number;

  constructor(private fb: FormBuilder) {}

  ngOnInit() {
    this.loadInitial();
  }

  ngAfterViewInit() {
    this.term.underlying.attachCustomKeyEventHandler((event) => {
      // prevent default handling of Ctrl-C/Ctrl-X (otherwise it resets the selections
      // and default Ctrl-C/X has nothing to copy) and Ctrl-V
      return !event.ctrlKey || 'cxv'.indexOf(event.key.toLowerCase()) === -1;
    });

    this.term.underlying.setOption('fontSize', 12);

    if (this.supportsStdin) {
      this.setupStdin(this.hasStdin);
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    for (const p in changes) {
      if (!changes[p].firstChange) {
        if (p === 'hasStdin' && this.supportsStdin) {
          this.setupStdin(changes[p].currentValue);
        } else if (p === 'follow') {
          this.onToggleFollow(changes[p].currentValue);
        }
      }
    }
  }

  ngOnDestroy() {
    this.follow = false; // avoid self-re-scheduling
    this.clearTimer();
    if (this.stdinSubscription) {
      this.stdinSubscription.unsubscribe();
    }
  }

  loadInitial() {
    this.content = '';
    this.initialEntry().subscribe((entry) => {
      if (entry == null) {
        this.content = 'File not found';
        this.term.write('File not found');
        return;
      }
      let offset = 0;
      if (entry.size > MAX_TAIL) {
        offset = entry.size - MAX_TAIL;
      }
      this.offset = offset;
      this.updateTail();
    });
  }

  updateTail() {
    this.contentFetcher(this.offset, 0).subscribe((chunk) => {
      if (chunk) {
        if (this.supportsStdin && this.hasStdin) {
          this.clearInput();
        }
        this.content += chunk.content;
        this.offset = chunk.endPointer;
        this.term.write(this.prepareLinebreaks(chunk.content));
        if (this.supportsStdin && this.hasStdin) {
          this.term.write(FileViewerComponent.SC); // save cursor
          this.updateInput();
        }
      }
      if (this.follow) {
        this.timer = setTimeout(() => this.updateTail(), 1000);
      }
    });
  }

  public canFollow(): boolean {
    return typeof this.contentFetcher !== 'undefined';
  }

  onToggleFollow(checked: boolean) {
    this.follow = checked;
    if (checked) {
      this.timer = setTimeout(() => this.updateTail(), 1000);
    } else {
      this.clearTimer();
      if (this.supportsStdin && this.hasStdin) {
        this.clearInput();
        this.clearBuffer();
      }
    }
  }

  private clearTimer() {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  setupStdin(connect: boolean) {
    if (connect) {
      this.resizeDisposable = this.term.underlying.onResize((data) => {
        if (this.supportsStdin && this.hasStdin && this.follow) {
          setTimeout(
            (dim) => {
              this.clearInput();
              this.updateInput();
            },
            0,
            data
          );
        }
      });

      this.stdinSubscription = this.term.keyInput.subscribe((input) => {
        // keyboard input usually is a single charactor or CSI sequence, but clipboard content is a string
        if (!this.follow) {
          return;
        }
        // store initial position on first keystroke after terminal output
        if (!this.initialCursorX) {
          this.initialCursorX = this.cursorX;
        }

        let idx = 0;
        while (idx < input.length) {
          if (input[idx] === '\r') {
            idx++;
            this.sendStdin();
          } else if (input.charCodeAt(idx) === 0x7f) {
            idx++;
            if (this.bufferCursorPos > 0) {
              this.clearInput();
              this.buffer = this.buffer.substring(0, this.bufferCursorPos - 1) + this.buffer.substring(this.bufferCursorPos);
              this.bufferCursorPos--;
              this.updateInput();
            }
          } else {
            this.clearInput();
            if (input.charCodeAt(idx) >= 32) {
              this.buffer = [this.buffer.slice(0, this.bufferCursorPos), input[idx], this.buffer.slice(this.bufferCursorPos)].join('');
              this.bufferCursorPos++;
              idx++;
            } else if (input.substr(idx).startsWith(FileViewerComponent.CSI)) {
              idx += FileViewerComponent.CSI.length;
              if (idx < input.length) {
                switch (input[idx]) {
                  case 'C': // cursor right
                    this.bufferCursorPos = this.bufferCursorPos + (this.buffer.length > this.bufferCursorPos ? 1 : 0);
                    break;
                  case 'D': // cursor left
                    this.bufferCursorPos = this.bufferCursorPos - (this.bufferCursorPos > 0 ? 1 : 0);
                    break;
                  case 'H': // pos 1
                    this.bufferCursorPos = 0;
                    break;
                  case 'F': // end
                    this.bufferCursorPos = this.buffer.length;
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
    } else if (this.stdinSubscription) {
      this.stdinSubscription.unsubscribe();
      this.stdinSubscription = null;
      this.resizeDisposable.dispose();
      this.resizeDisposable = null;
    }
  }

  private prepareLinebreaks(s: string): string {
    return s.replace(/\r\n/g, '\n').replace(/\n/g, '\r\n');
  }

  public sendStdin() {
    this.clearInput();
    this.inputEvent.emit(this.buffer);
    this.clearBuffer();
    this.inputLineHeight_sav = 1;
  }

  private clearBuffer(): void {
    this.buffer = '';
    this.bufferCursorPos = 0;
    this.initialCursorX = undefined;
  }

  private clearInput(): void {
    this.term.write(FileViewerComponent.RC); // restore cursor
    this.term.write(FileViewerComponent.EL_TO_EOL); // clear from cursor to EOL
    for (let i = 0; i < this.inputLineHeight_sav - 1; i++) {
      this.term.write(FileViewerComponent.CNL); // next line
      this.term.write(FileViewerComponent.EL_TO_EOL); // clear whole line
    }
    this.term.write(FileViewerComponent.RC); // restore cursor
  }

  private updateInput(): void {
    this.term.write(this.buffer);
    this.term.write(FileViewerComponent.RC); // restore cursor
    this.term.write(this.buffer.substr(0, this.bufferCursorPos));
    this.inputLineHeight_sav = Math.max(this.inputLineHeight_sav, this.getInputLineCount());
  }

  public getInputLineCount() {
    return this.buffer ? Math.ceil((this.initialCursorX + this.buffer.length) / this.termCols) : 1;
  }

  public getLineOfCursor() {
    if (!this.buffer) {
      return 0;
    }
    const totalLen = this.initialCursorX + this.bufferCursorPos;
    const lineNo = Math.floor(totalLen / this.termCols);
    const xPos = totalLen % this.termCols;
    return totalLen > 0 && xPos === 0 ? lineNo - 1 : lineNo;
  }
}
