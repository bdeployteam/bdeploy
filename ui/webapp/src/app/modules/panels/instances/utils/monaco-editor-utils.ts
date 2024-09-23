import * as monaco from 'monaco-editor';

// a hack. it is a copy of monaco.MarkerSeverity as it cannot be imported directly for some reason
enum MarkerSeverity {
  Hint = 1,
  Info = 2,
  Warning = 4,
  Error = 8,
}

export function errorMarker(message: string, match: monaco.editor.FindMatch): monaco.editor.IMarkerData {
  return {
    severity: MarkerSeverity.Error,
    startLineNumber: match.range.startLineNumber,
    startColumn: match.range.startColumn,
    endLineNumber: match.range.endLineNumber,
    endColumn: match.range.endColumn,
    message,
  };
}
