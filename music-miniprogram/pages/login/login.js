// pages/login/login.js
const api = require('../../utils/request.js');

Page({
  data: {
    isLogin: true,
    loading: false,
    form: {
      username: '',
      password: '',
      email: ''
    }
  },

  switchTab(e) {
    this.setData({ isLogin: e.currentTarget.value === 'login' });
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: e.detail.value });
  },

  async submit() {
    const { username, password, email } = this.data.form;
    if (!username || !password) {
      wx.showToast({ title: '请填写完整', icon: 'none' });
      return;
    }
    this.setData({ loading: true });
    try {
      const data = this.data.isLogin
        ? await api.login(username, password)
        : await api.register(username, password, email);

      wx.setStorageSync('token', data.token);
      wx.setStorageSync('userInfo', data.user);

      wx.showToast({ title: this.data.isLogin ? '登录成功' : '注册成功', icon: 'success' });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/index/index' });
      }, 800);
    } catch (e) {
      // toast 已弹
    }
    this.setData({ loading: false });
  }
});
