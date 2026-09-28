// pages/song-detail/song-detail.js
const api = require('../../utils/request.js');

Page({
  data: {
    songId: null,
    song: null,
    related: [],
    isLiked: false
  },

  onLoad(options) {
    this.setData({ songId: options.id });
    this.loadDetail();
  },

  async loadDetail() {
    try {
      const data = await api.songDetail(this.data.songId);
      this.setData({
        song: data.song,
        related: data.related || []
      });
      // 检查点赞状态
      const userInfo = wx.getStorageSync('userInfo');
      if (userInfo) {
        try {
          const liked = await api.likedSongs(userInfo.id);
          const isLiked = liked.some(s => s.id == this.data.songId);
          this.setData({ isLiked });
        } catch (e) {}
      }
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  async toggleLike() {
    const userInfo = wx.getStorageSync('userInfo');
    if (!userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      setTimeout(() => wx.navigateTo({ url: '/pages/login/login' }), 600);
      return;
    }
    try {
      const data = await api.toggleLike(this.data.songId, userInfo.id);
      this.setData({ isLiked: data.liked });
      wx.showToast({ title: data.liked ? '已点赞' : '已取消', icon: 'none' });
    } catch (e) {}
  },

  playSong() {
    const externalUrl = this.data.song && this.data.song.externalUrl;
    if (externalUrl) {
      wx.setClipboardData({
        data: externalUrl,
        success: () => {
          wx.showModal({
            title: '播放提示',
            content: '歌曲链接已复制到剪贴板，可在浏览器中打开网易云音乐试听。',
            showCancel: false,
            confirmText: '好的'
          });
        }
      });
    }
  },

  goRelated(e) {
    const id = e.currentTarget.dataset.id;
    this.setData({ songId: id, isLiked: false, song: null, related: [] });
    this.loadDetail();
    wx.pageScrollTo({ scrollTop: 0 });
  },

  formatNum(n) {
    if (!n) return '0';
    if (n >= 10000) return (n / 10000).toFixed(1) + 'w';
    return n.toString();
  }
});
