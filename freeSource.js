// 免费音源模块 — Huibq + QQ CDN 完整播放
const HUIBQ_BASE = 'https://lxmusicapi.onrender.com';
const HUIBQ_KEY = 'share-v3';
const QQ_SEARCH_URL = 'https://u.y.qq.com/cgi-bin/musicu.fcg';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36';

async function fetchJSON(url, opts = {}) {
  const { method = 'GET', headers = {}, body, timeout = 12000 } = opts;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    const o = { method, headers: { 'User-Agent': UA, ...headers }, signal: controller.signal };
    if (body != null) { o.body = typeof body === 'string' ? body : JSON.stringify(body); o.headers['Content-Type'] = 'application/json'; }
    const resp = await fetch(url, o);
    const text = await resp.text();
    try { return JSON.parse(text); } catch (_) { return text; }
  } finally { clearTimeout(timer); }
}

async function getPlayUrl({ source, songId, quality = '320k' } = {}) {
  const data = await fetchJSON(`${HUIBQ_BASE}/url/${source}/${encodeURIComponent(String(songId))}/${quality}`, {
    headers: { 'X-Request-Key': HUIBQ_KEY }, timeout: 10000,
  });
  if (data && data.code === 0) {
    const url = data.url || data.data;
    if (url && typeof url === 'string') return { url, quality };
  }
  throw new Error(data && data.msg || '获取播放地址失败');
}

async function qqSearch({ keywords, limit = 3 } = {}) {
  const body = { req_1: { method: 'DoSearchForQQMusicDesktop', module: 'music.search.SearchCgiService', param: { num_per_page: Math.min(limit || 3, 10), page_num: 1, query: String(keywords || ''), search_type: 0 } } };
  const data = await fetchJSON(QQ_SEARCH_URL, { method: 'POST', headers: { Referer: 'https://y.qq.com' }, body, timeout: 8000 });
  const list = data && data.req_1 && data.req_1.data && data.req_1.data.body && data.req_1.data.body.song && data.req_1.data.body.song.list;
  if (!Array.isArray(list)) return [];
  return list.filter(Boolean).map(function(item) { return { id: String(item.mid || ''), name: String(item.name || ''), mid: String(item.mid || '') }; });
}

module.exports = { getPlayUrl, qqSearch };
