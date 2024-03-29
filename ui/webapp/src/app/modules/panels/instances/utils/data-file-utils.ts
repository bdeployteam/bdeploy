import { Base64 } from 'js-base64';
import { DataFilePath, FileListEntry } from 'src/app/modules/primary/instances/services/data-files.service';

export function encodeDataFilePath(dfp: { minion: string; path: string }): string {
  return Base64.encode(JSON.stringify({ minion: dfp.minion, path: dfp.path }));
}

export function decodeDataFilePath(encodedPath: string): { minion: string; path: string } {
  try {
    return JSON.parse(Base64.decode(encodedPath));
  } catch {
    return { minion: '', path: '' };
  }
}

export function findDataFilePath(dfp: DataFilePath, path: string): DataFilePath {
  if (!dfp) {
    return null;
  }
  if (dfp.path === path) {
    return dfp;
  }
  return dfp.children.map((child) => findDataFilePath(child, path)).find((i) => !!i);
}

export function toFileList(dfp: DataFilePath): FileListEntry[] {
  if (dfp.children.length === 0) {
    return [dfp];
  }
  return dfp.children.flatMap((i) => toFileList(i));
}

export function constructDataFilePaths(
  minion: string,
  entries: FileListEntry[],
  selectPath: (p: DataFilePath) => void,
): DataFilePath {
  const root: DataFilePath = {
    minion,
    name: minion,
    path: '',
    crumbs: [],
    directory: undefined,
    entry: undefined,
    children: [],
    lastModified: undefined,
    size: undefined,
  };
  root.crumbs.push({ label: minion, onClick: () => selectPath(root) });
  for (const e of entries) {
    const paths = e.entry.path.replace('\\', '/').split('/');
    addNode(minion, root, e, paths, selectPath);
  }
  calculateSizeAndLastModifiedDate(root);
  sortDataFiles(root);
  return root;
}

function calculateSizeAndLastModifiedDate(node: DataFilePath) {
  if (node.entry) return; // for file lastModified and size are already set
  node.children.forEach((child) => calculateSizeAndLastModifiedDate(child));
  node.lastModified = Math.max(...node.children.map((child) => child.lastModified));
  node.size = node.children.map((child) => child.size).reduce((a, b) => a + b, 0);
}

function addNode(
  minion: string,
  parent: DataFilePath,
  entry: FileListEntry,
  paths: string[],
  selectPath: (p: DataFilePath) => void,
) {
  const name = paths.shift();
  const path = [parent.path, name].filter((i) => !!i).join('/');
  if (paths.length === 0) {
    const leaf: DataFilePath = {
      crumbs: [...parent.crumbs],
      minion,
      name,
      path,
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

  let node: DataFilePath = parent.children.find((node) => node.path === path);
  if (!node) {
    node = {
      crumbs: [...parent.crumbs],
      minion,
      name,
      path,
      directory: entry.directory,
      entry: undefined,
      children: [],
      lastModified: undefined, // will be calculated after tree is constructed
      size: undefined, // will be calculated after tree is constructed
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
function sortDataFiles(dfp: DataFilePath) {
  dfp.children.sort((a, b) => {
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
  dfp.children.forEach((child) => sortDataFiles(child));
}
