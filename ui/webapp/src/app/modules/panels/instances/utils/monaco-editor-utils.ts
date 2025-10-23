import { editor, MarkerSeverity } from 'monaco-editor';

export function errorMarker(message: string, match: editor.FindMatch): editor.IMarkerData {
  return {
    severity: MarkerSeverity.Error,
    startLineNumber: match.range.startLineNumber,
    startColumn: match.range.startColumn,
    endLineNumber: match.range.endLineNumber,
    endColumn: match.range.endColumn,
    message,
  };
}
