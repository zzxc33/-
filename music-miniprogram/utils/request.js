// utils/request.js
const BASE_URL = 'http://localhost:8080';

function request(options) {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token');
    const header = {
      'content-type': 'application/json',
      ...(options.header || {})
    };
    if (token) {
      header['Authorization'] = 'Bearer ' + token;
    }

    wx.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header,
      success(res) {
        const body = res.data;
        if (body && body.code === 0) {
          resolve(body.data);
        } else if (body && body.code === 401) {
          wx.removeStorageSync('token');
          wx.removeStorageSync('userInfo');
          wx.showToast({ title: '请先登录', icon: 'none' });
          reject(body);
        } else {
          wx.showToast({ title: (body && body.msg) || '请求失败', icon: 'none' });
          reject(body);
        }
      },
      fail(err) {
        wx.showToast({ title: '网络错误', icon: 'none' });
        reject(err);
      }
    });
  });
}

module.exports = {
  home: (userId) => request({ url: `/api/home?userId=${userId || 0}` }),
  recommend: (userId, limit) => request({ url: `/api/recommend?userId=${userId || 0}&limit=${limit || 12}` }),
  songs: () => request({ url: '/api/songs' }),
  hotSongs: (limit) => request({ url: `/api/songs/hot?limit=${limit || 10}` }),
  newSongs: (limit) => request({ url: `/api/songs/new?limit=${limit || 10}` }),
  songDetail: (id) => request({ url: `/api/songs/${id}` }),
  search: (keyword) => request({ url: `/api/songs/search?keyword=${encodeURIComponent(keyword)}` }),
  genres: () => request({ url: '/api/songs/genres' }),
  songsByGenre: (genre) => request({ url: `/api/songs/genre/${encodeURIComponent(genre)}` }),
  recordPlay: (id, userId) => request({ url: `/api/songs/${id}/play?userId=${userId || 0}`, method: 'POST' }),
  toggleLike: (id, userId) => request({ url: `/api/songs/${id}/like?userId=${userId}`, method: 'POST' }),
  likedSongs: (userId) => request({ url: `/api/users/${userId}/liked` }),
  hotPlaylists: (limit) => request({ url: `/api/playlists?limit=${limit || 10}` }),
  playlistDetail: (id) => request({ url: `/api/playlists/${id}` }),
  login: (username, password) => request({
    url: '/api/auth/login', method: 'POST', data: { username, password }
  }),
  register: (username, password, email) => request({
    url: '/api/auth/register', method: 'POST', data: { username, password, email }
  }),
  me: () => request({ url: '/api/auth/me' }),
  logout: () => request({ url: '/api/auth/logout', method: 'POST' })
};
