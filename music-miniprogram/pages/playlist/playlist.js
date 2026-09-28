// pages/playlist/playlist.js
const api = require('../../utils/request.js');

Page({
  data: {
    playlists: [],
    loading: false
  },

  onShow() {
    this.loadData();
  },

  async loadData() {
    this.setData({ loading: true });
    try {
      const playlists = await api.hotPlaylists(20);
      this.setData({ playlists });
    } catch (e) {}
    this.setData({ loading: false });
  },

  goDetail(e) {
    wx.showToast({ title: '歌单详情开发中', icon: 'none' });
  }
});
