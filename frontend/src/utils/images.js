const API_BASE = process.env.REACT_APP_API_URL || 'http://localhost:9091';

export function imgUrl(path) {
  if (!path) return null;
  if (path.startsWith('http://') || path.startsWith('https://')) return path;
  if (path.startsWith('/uploads/')) return API_BASE + path;
  return path;
}
