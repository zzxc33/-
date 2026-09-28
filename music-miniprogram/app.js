// app.js
App({
  onLaunch() {
    console.log('音乐推荐小程序启动');
    const token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
    }
  },

  globalData: {
    baseUrl: 'http://localhost:8080',
    token: '',
    userInfo: null
  }
});
