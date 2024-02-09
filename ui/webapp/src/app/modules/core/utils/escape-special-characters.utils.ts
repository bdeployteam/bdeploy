const SPECIAL_YAML_CHARACTERS = ['\\', ':', ';', '_', '(', ')', '@', '$', '%', '^', '&', ','];

export enum SpecialCharacterType {
  XML = 'XML',
  YAML = 'YAML',
  JSON = 'JSON',
}

export function escapeSpecialCharacters(type: SpecialCharacterType, unescaped: string): string {
  switch (type) {
    case SpecialCharacterType.XML:
      return escapeXmlCharacters(unescaped);
    case SpecialCharacterType.JSON:
      return escapeJsonCharacters(unescaped);
    case SpecialCharacterType.YAML:
      return escapeYamlCharacters(unescaped);
  }
}

function escapeXmlCharacters(unescaped: string): string {
  return unescaped
    .replace(/'/g, '&apos;') // Replace ' with &apos;
    .replace(/"/g, '&quot;') // Replace " with &quot;
    .replace(/&/g, '&amp;') // Replace & with &amp;
    .replace(/</g, '&lt;') // Replace < with &lt;
    .replace(/>/g, '&gt;'); // Replace > with &gt;
}

function escapeJsonCharacters(unescaped: string): string {
  return unescaped
    .replace(/x08/g, '\\b') // Backspace is replaced with \b
    .replace(/\f/g, '\\f') // Form feed is replaced with \f
    .replace(/\n/g, '\\n') // Newline is replaced with \n
    .replace(/\r/g, '\\r') // Carriage return is replaced with \r
    .replace(/\t/g, '\\t') // Tab is replaced with \t
    .replace(/"/g, '\\"') // Double quote is replaced with \"
    .replace(/\\/g, '\\\\'); // Backslash is replaced with \\
}

function escapeYamlCharacters(unescaped: string): string {
  let hasSpecialCharacters = false;
  for (const special of SPECIAL_YAML_CHARACTERS) {
    if (unescaped.includes(special)) {
      hasSpecialCharacters = true;
      break;
    }
  }

  if (hasSpecialCharacters) {
    return `"${unescaped.replace(/"/g, '\\"')}"`;
  } else {
    return unescaped;
  }
}
