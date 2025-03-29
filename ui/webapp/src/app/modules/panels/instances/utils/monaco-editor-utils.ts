import * as monaco from 'monaco-editor';

export function errorMarker(message: string, match: monaco.editor.FindMatch): monaco.editor.IMarkerData {
  return {
    severity: monaco.MarkerSeverity.Error,
    startLineNumber: match.range.startLineNumber,
    startColumn: match.range.startColumn,
    endLineNumber: match.range.endLineNumber,
    endColumn: match.range.endColumn,
    message,
  };
}
