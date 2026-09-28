// pages/mine/mine.js
const api = require('../../utils/request.js');

Page({
  data: {
    userInfo: null,
    likedSongs: []
  },

  onShow() {
    const userInfo = wx.getStorageSync('userInfo');
    this.setData({ userInfo });
    if (userInfo) {
      this.loadData();
    }
  },

  async loadData() {
    try {
      const likedSongs = await api.likedSongs(this.data.userInfo.id);
      this.setData({ likedSongs });
    } catch (e) {}
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' });
  },

  goDetail(e) {
    const song = e.currentTarget.dataset.song;
    wx.navigateTo({ url: `/pages/song-detail/song-detail?id=${song.id}` });
  },

  doLogout() {
    wx.showModal({
      title: '确认退出？',
      success: async (res) => {
        if (res.confirm) {
          await api.logout();
          wx.removeStorageSync('token');
          wx.removeStorageSync('userInfo');
          this.setData({ userInfo: null, likedSongs: [] });
          wx.showToast({ title: '已退出', icon: 'success' });
        }
      }
    });
  }
});
