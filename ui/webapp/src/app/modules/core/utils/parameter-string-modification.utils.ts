const SPECIAL_YAML_CHARACTERS = ['\\', ':', ';', '_', '(', ')', '@', '$', '%', '^', '&', ','];

export enum StringModificationType {
  FILEURI = 'FILEURI',
  XML = 'XML',
  YAML = 'YAML',
  JSON = 'JSON',
}

export function modifyString(type: StringModificationType, unmodified: string): string {
  switch (type) {
    case StringModificationType.FILEURI:
      return 'file://' + unmodified.replaceAll('\\', '/');
    case StringModificationType.XML:
      return escapeXmlCharacters(unmodified);
    case StringModificationType.JSON:
      return escapeJsonCharacters(unmodified);
    case StringModificationType.YAML:
      return escapeYamlCharacters(unmodified);
  }
}

function escapeXmlCharacters(unmodified: string): string {
  return unmodified
    .replace(/'/g, '&apos;') // Replace ' with &apos;
    .replace(/"/g, '&quot;') // Replace " with &quot;
    .replace(/&/g, '&amp;') // Replace & with &amp;
    .replace(/</g, '&lt;') // Replace < with &lt;
    .replace(/>/g, '&gt;'); // Replace > with &gt;
}

function escapeJsonCharacters(unmodified: string): string {
  return unmodified
    .replace(/x08/g, '\\b') // Backspace is replaced with \b
    .replace(/\f/g, '\\f') // Form feed is replaced with \f
    .replace(/\n/g, '\\n') // Newline is replaced with \n
    .replace(/\r/g, '\\r') // Carriage return is replaced with \r
    .replace(/\t/g, '\\t') // Tab is replaced with \t
    .replace(/"/g, '\\"') // Double quote is replaced with \"
    .replace(/\\/g, '\\\\'); // Backslash is replaced with \\
}

function escapeYamlCharacters(unmodified: string): string {
  let hasSpecialCharacters = false;
  for (const special of SPECIAL_YAML_CHARACTERS) {
    if (unmodified.includes(special)) {
      hasSpecialCharacters = true;
      break;
    }
  }

  if (hasSpecialCharacters) {
    return `"${unmodified.replace(/"/g, '\\"')}"`;
  } else {
    return unmodified;
  }
}
