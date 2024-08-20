import { Base64 } from 'js-base64';
import { FileListEntry, FilePath } from 'src/app/modules/primary/instances/services/files.service';

export function encodeFilePath(filePath: { minion: string; path: string }): string {
  return Base64.encode(JSON.stringify({ minion: filePath.minion, path: filePath.path }));
}

export function decodeFilePath(encodedPath: string): { minion: string; path: string } {
  try {
    return JSON.parse(Base64.decode(encodedPath));
  } catch {
    return { minion: '', path: '' };
  }
}

export function getDescendants(filePath: FilePath): FilePath[] {
  if (!filePath) {
    return null;
  }
  const arr = [filePath];
  filePath.children?.flatMap((child) => getDescendants(child)).forEach((descendant) => arr.push(descendant));
  return arr;
}

export function findFilePath(filePath: FilePath, path: string): FilePath {
  if (!filePath) {
    return null;
  }
  if (filePath.path === path) {
    return filePath;
  }
  return filePath.children.map((child) => findFilePath(child, path)).find((i) => !!i);
}

export function toFileList(filePath: FilePath): FileListEntry[] {
  if (filePath.children.length === 0) {
    return [filePath];
  }
  return filePath.children.flatMap((i) => toFileList(i));
}

export function constructFilePath(
  minion: string,
  entries: FileListEntry[],
  selectPath: (p: FilePath) => void,
): FilePath {
  const root: FilePath = {
    minion,
    name: minion,
    path: '',
    crumbs: [],
    directory: null,
    entry: null,
    children: [],
    lastModified: null,
    size: null,
  };
  root.crumbs.push({ label: minion, onClick: () => selectPath(root) });
  for (const e of entries) {
    const paths = e.entry.path.replace('\\', '/').split('/');
    addNode(minion, root, e, paths, selectPath);
  }
  calculateSizeAndLastModifiedDate(root);
  sortFiles(root);
  return root;
}

function calculateSizeAndLastModifiedDate(node: FilePath) {
  if (node.entry) return; // for file lastModified and size are already set
  node.children.forEach((child) => calculateSizeAndLastModifiedDate(child));
  node.lastModified = Math.max(...node.children.map((child) => child.lastModified));
  node.size = node.children.map((child) => child.size).reduce((a, b) => a + b, 0);
}

function addNode(
  minion: string,
  parent: FilePath,
  entry: FileListEntry,
  paths: string[],
  selectPath: (p: FilePath) => void,
) {
  const name = paths.shift();
  const path = [parent.path, name].filter((i) => !!i).join('/');
  if (paths.length === 0) {
    const leaf: FilePath = {
      minion,
      name,
      path,
      crumbs: [...parent.crumbs],
      directory: entry.directory,
      entry: entry.entry,
      children: [],
      lastModified: entry.entry.lastModified,
      size: entry.entry.size,
    };
    leaf.crumbs.push({ label: name, onClick: () => selectPath(leaf) });
    parent.children.push(leaf);
    return;
  }

  let node: FilePath = parent.children.find((filePath) => filePath.path === path);
  if (!node) {
    node = {
      minion,
      name,
      path,
      crumbs: [...parent.crumbs],
      directory: entry.directory,
      entry: null,
      children: [],
      lastModified: null, // will be calculated after tree is constructed
      size: null, // will be calculated after tree is constructed
    };
    node.crumbs.push({ label: name, onClick: () => selectPath(node) });
    parent.children.push(node);
  }
  addNode(minion, node, entry, paths, selectPath);
}

/*
  Folders first, Files last
  Folders are sorted by names asc
  Files are sorted by lastModified desc
  */
function sortFiles(filePath: FilePath) {
  filePath.children.sort((a, b) => {
    const aIsFolder = a.children.length;
    const bIsFolder = b.children.length;
    if (aIsFolder && bIsFolder) {
      return a.name.localeCompare(b.name);
    }
    if (!aIsFolder && !bIsFolder) {
      return b.entry.lastModified - a.entry.lastModified;
    }
    return aIsFolder ? -1 : 1;
  });
  filePath.children.forEach((child) => sortFiles(child));
}
