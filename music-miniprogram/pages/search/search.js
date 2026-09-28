// pages/search/search.js
const api = require('../../utils/request.js');

Page({
  data: {
    keyword: '',
    results: [],
    loading: false,
    hotKeywords: ['周杰伦', '流行', '摇滚', '民谣', '林俊杰', '电子']
  },

  async doSearch() {
    if (!this.data.keyword.trim()) return;
    this.setData({ loading: true });
    try {
      const results = await api.search(this.data.keyword.trim());
      this.setData({ results });
    } catch (e) {
      this.setData({ results: [] });
    }
    this.setData({ loading: false });
  },

  onInput(e) {
    this.setData({ keyword: e.detail.value });
  },

  onConfirm() {
    this.doSearch();
  },

  clearKeyword() {
    this.setData({ keyword: '', results: [] });
  },

  useHotKeyword(e) {
    const k = e.currentTarget.dataset.k;
    this.setData({ keyword: k });
    this.doSearch();
  },

  goDetail(e) {
    const song = e.currentTarget.dataset.song;
    const userInfo = wx.getStorageSync('userInfo');
    api.recordPlay(song.id, userInfo ? userInfo.id : 0);
    wx.navigateTo({ url: `/pages/song-detail/song-detail?id=${song.id}` });
  }
});
