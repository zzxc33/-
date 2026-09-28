/**
 * 登录页面 JavaScript
 * 智能音乐推荐与播放平台
 */

document.addEventListener('DOMContentLoaded', function () {

    // ============================================
    // 1. 生成背景音乐音符粒子
    // ============================================
    function createMusicNotes() {
        const container = document.querySelector('.music-notes');
        if (!container) return;

        const notes = ['♩', '♪', '♫', '♬', '🎵', '🎶'];
        const colors = [
            'rgba(245, 158, 11, 0.18)',
            'rgba(249, 115, 22, 0.14)',
            'rgba(167, 139, 250, 0.12)',
            'rgba(34, 211, 238, 0.10)',
            'rgba(252, 211, 77, 0.16)'
        ];

        for (let i = 0; i < 25; i++) {
            const note = document.createElement('div');
            note.className = 'note';
            note.textContent = notes[Math.floor(Math.random() * notes.length)];
            note.style.left = Math.random() * 100 + '%';
            note.style.fontSize = (Math.random() * 20 + 16) + 'px';
            note.style.color = colors[Math.floor(Math.random() * colors.length)];
            note.style.animationDuration = (Math.random() * 15 + 12) + 's';
            note.style.animationDelay = (Math.random() * 20) + 's';
            container.appendChild(note);
        }
    }

    createMusicNotes();

    // ============================================
    // 2. 输入框焦点效果
    // ============================================
    document.querySelectorAll('.input-wrapper input').forEach(function (input) {
        const wrapper = input.closest('.input-wrapper');

        input.addEventListener('focus', function () {
            wrapper.classList.add('focused');
        });

        input.addEventListener('blur', function () {
            if (!input.value) {
                wrapper.classList.remove('focused');
            }
        });

        // 如果输入框有值，保持焦点状态
        if (input.value) {
            wrapper.classList.add('focused');
        }
    });

    // ============================================
    // 3. 密码显示/隐藏切换
    // ============================================
    document.querySelectorAll('.toggle-password').forEach(function (btn) {
        btn.addEventListener('click', function () {
            const input = this.closest('.input-wrapper').querySelector('input');
            const type = input.getAttribute('type') === 'password' ? 'text' : 'password';
            input.setAttribute('type', type);
            this.textContent = type === 'password' ? '👁️' : '👁️‍🗨️';
        });
    });

    // ============================================
    // 4. 验证码刷新
    // ============================================
    const captchaImg = document.getElementById('captchaImg');
    if (captchaImg) {
        captchaImg.addEventListener('click', function () {
            this.src = '/captcha?' + new Date().getTime();
        });
    }

    // ============================================
    // 5. 表单验证与提交
    // ============================================
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', function (e) {
            e.preventDefault();

            // 重置错误状态
            clearErrors();

            // 获取表单数据
            const username = document.getElementById('username').value.trim();
            const password = document.getElementById('password').value.trim();
            const captcha = document.getElementById('captcha') ? document.getElementById('captcha').value.trim() : '';

            // 验证
            let isValid = true;

            if (!username) {
                showError('username', '请输入用户名');
                isValid = false;
            }

            if (!password) {
                showError('password', '请输入密码');
                isValid = false;
            }

            if (document.getElementById('captcha') && !captcha) {
                showError('captcha', '请输入验证码');
                isValid = false;
            }

            if (!isValid) return;

            // 显示加载状态
            const submitBtn = document.getElementById('loginBtn');
            submitBtn.classList.add('loading');
            submitBtn.disabled = true;

            // 构建表单数据
            const formData = new URLSearchParams();
            formData.append('username', username);
            formData.append('password', password);
            if (captcha) {
                formData.append('captcha', captcha);
            }

            // 添加记住我
            const rememberMe = document.getElementById('rememberMe');
            if (rememberMe && rememberMe.checked) {
                formData.append('rememberMe', 'true');
            }

            // 发送登录请求
            fetch('/doLogin', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: formData.toString()
            })
                .then(function (response) {
                    if (response.redirected) {
                        // 登录成功，跳转到首页
                        window.location.href = response.url;
                    } else if (response.ok) {
                        // 检查是否是JSON响应（登录失败的情况）
                        const contentType = response.headers.get('content-type');
                        if (contentType && contentType.includes('application/json')) {
                            return response.json();
                        }
                        // 否则重定向到登录页（含错误参数）
                        window.location.href = '/login?error=true';
                    } else {
                        window.location.href = '/login?error=true';
                    }
                })
                .then(function (data) {
                    if (data && data.error) {
                        showFormError(data.error || '用户名或密码错误！');
                    }
                })
                .catch(function () {
                    showFormError('网络错误，请稍后重试！');
                })
                .finally(function () {
                    submitBtn.classList.remove('loading');
                    submitBtn.disabled = false;
                });
        });
    }

    // ============================================
    // 6. 错误提示辅助函数
    // ============================================
    function showError(fieldId, message) {
        const input = document.getElementById(fieldId);
        if (input) {
            input.classList.add('error');
            input.closest('.input-wrapper').classList.add('error');
        }

        // 显示表单错误消息
        const errorDiv = document.getElementById('formError');
        if (errorDiv) {
            errorDiv.textContent = message;
            errorDiv.classList.add('show');
        }
    }

    function showFormError(message) {
        const errorDiv = document.getElementById('formError');
        if (errorDiv) {
            errorDiv.textContent = message;
            errorDiv.classList.add('show');
        }
    }

    function clearErrors() {
        document.querySelectorAll('.input-wrapper input.error').forEach(function (el) {
            el.classList.remove('error');
            el.closest('.input-wrapper').classList.remove('error');
        });

        const errorDiv = document.getElementById('formError');
        if (errorDiv) {
            errorDiv.classList.remove('show');
        }
    }

    // ============================================
    // 7. 键盘快捷键：回车提交
    // ============================================
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            const form = document.getElementById('loginForm');
            if (form && document.activeElement && document.activeElement.closest('#loginForm')) {
                form.dispatchEvent(new Event('submit'));
            }
        }
    });
});