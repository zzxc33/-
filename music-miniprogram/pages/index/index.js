// pages/index/index.js
const api = require('../../utils/request.js');

Page({
  data: {
    recommendList: [],
    hotList: [],
    newList: [],
    genres: [],
    currentGenre: null,
    greeting: '晚上好'
  },

  onShow() {
    this.setGreeting();
    this.loadData();
  },

  setGreeting() {
    const h = new Date().getHours();
    let g = '晚上好';
    if (h < 6) g = '夜深了';
    else if (h < 12) g = '早上好';
    else if (h < 14) g = '中午好';
    else if (h < 18) g = '下午好';
    this.setData({ greeting: g });
  },

  async loadData() {
    try {
      const userInfo = wx.getStorageSync('userInfo');
      const userId = userInfo ? userInfo.id : 0;
      const data = await api.home(userId);
      this.setData({
        recommendList: data.recommend || [],
        hotList: data.hot || [],
        newList: data.new || [],
        genres: data.genres || []
      });
    } catch (e) {
      console.error('首页加载失败', e);
    }
  },

  goDetail(e) {
    const song = e.currentTarget.dataset.song;
    const userInfo = wx.getStorageSync('userInfo');
    api.recordPlay(song.id, userInfo ? userInfo.id : 0);
    wx.navigateTo({ url: `/pages/song-detail/song-detail?id=${song.id}` });
  },

  async filterByGenre(e) {
    const genre = e.currentTarget.dataset.genre;
    const current = this.data.currentGenre;
    if (current === genre) {
      this.setData({ currentGenre: null });
      this.loadData();
      return;
    }
    this.setData({ currentGenre: genre });
    try {
      const list = await api.songsByGenre(genre);
      this.setData({ hotList: list });
    } catch (e) {}
  },

  formatNum(n) {
    if (!n) return '0';
    if (n >= 10000) return (n / 10000).toFixed(1) + 'w';
    return n.toString();
  }
});
