// ========== 音乐播放器 ==========
const Player = {
    currentSong: null,
    playlist: [],
    currentIndex: -1,
    isPlaying: false,
    audio: null,
    volume: 0.7,
    loopMode: 'list', // list | single | shuffle
    shufflePlaylist: [],
    shuffleIndex: -1,
    likedSongs: new Set(JSON.parse(localStorage.getItem('likedSongs') || '[]')),

    init() {
        this.audio = new Audio();
        this.audio.volume = this.volume;

        // 🟢 DOM 引用缓存（避免每次 timeupdate 都 querySelector）
        this._progressFill = document.querySelector('.progress-fill');
        this._progressThumb = document.querySelector('.progress-thumb');
        this._currentTime = document.querySelector('.player-current');
        this._duration = document.querySelector('.player-duration');

        // 绑定音频事件
        this.audio.addEventListener('timeupdate', () => this.updateProgress());
        this.audio.addEventListener('ended', () => this.onEnded());
        this.audio.addEventListener('loadedmetadata', () => {
            if (this._duration) this._duration.textContent = this.formatTime(this.audio.duration);
        });

        // 进度条自动隐藏
        this.initProgressAutoHide();

        // 绑定键盘快捷键
        this.initKeyboardShortcuts();

        // 更新循环模式按钮
        this.updateLoopBtn();

        // 🟢 P3-7: 恢复上次的播放队列 + 当前歌曲（刷新续播体验）
        this._restoreFromStorage();
    },

    // ========== 🟢 P3-7: 播放队列持久化 ==========
    _restoreFromStorage() {
        try {
            const q = JSON.parse(localStorage.getItem('player_queue') || '[]');
            const cur = JSON.parse(localStorage.getItem('player_current') || 'null');
            if (q.length > 0) {
                this.playlist = q;
                this.currentIndex = cur ? q.findIndex(s => s.id === cur.id) : -1;
                if (cur && this.currentIndex >= 0) {
                    this.currentSong = cur;
                    // 只恢复 UI（不自动 play，尊重用户）
                    if (typeof this.updateQueueList === 'function') this.updateQueueList();
                    const pCover = document.querySelector('.player-cover img');
                    const pSong = document.querySelector('.player-song');
                    const pArtist = document.querySelector('.player-artist');
                    if (pCover) pCover.src = cur.coverUrl || '/images/default-cover.svg';
                    if (pSong) pSong.textContent = cur.title;
                    if (pArtist) pArtist.textContent = cur.artist;
                }
            }
        } catch (e) { /* ignore */ }
    },
    _saveToStorage() {
        try {
            localStorage.setItem('player_queue', JSON.stringify(this.playlist.slice(0, 50)));
            localStorage.setItem('player_current', JSON.stringify(this.currentSong));
        } catch (e) { /* quota or private mode */ }
    },

    // ========== 键盘快捷键 ==========
    initKeyboardShortcuts() {
        document.addEventListener('keydown', (e) => {
            // 不在输入框中才生效
            const tag = e.target.tagName;
            if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;

            switch (e.code) {
                case 'Space':
                    e.preventDefault();
                    this.togglePlay();
                    break;
                case 'ArrowRight':
                    e.preventDefault();
                    this.next();
                    break;
                case 'ArrowLeft':
                    e.preventDefault();
                    this.prev();
                    break;
                case 'KeyR':
                    e.preventDefault();
                    this.cycleLoopMode();
                    break;
                case 'KeyS':
                    e.preventDefault();
                    if (this.currentSong) this.toggleLikeCurrent();
                    break;
            }
        });
    },

    // ========== 更新播放列表面板 ==========
    updateQueueList() {
        const listEl = document.getElementById('queue-list');
        const countEl = document.getElementById('queue-count');
        if (!listEl) return;

        const list = this.playlist || [];

        if (countEl) countEl.textContent = `${list.length} 首`;

        if (list.length === 0) {
            listEl.innerHTML = `
                <div class="queue-empty">
                    <div class="empty-icon" style="font-size:48px;opacity:0.3;margin-bottom:16px;">🎵</div>
                    <p>播放列表为空，选择歌曲开始播放吧</p>
                </div>`;
            return;
        }

        listEl.innerHTML = list.map((song, i) => {
            const isActive = i === this.currentIndex;
            return `
                <div class="queue-item ${isActive ? 'active' : ''}" data-index="${i}">
                    <div class="queue-item-index">
                        ${isActive
                            ? '<span class="playing-bar"><span></span><span></span><span></span></span>'
                            : `<span class="idx">${String(i + 1).padStart(2, '0')}</span>`}
                    </div>
                    <div class="queue-item-cover">
                        <img src="${song.coverUrl || '/images/default-cover.svg'}" alt="">
                    </div>
                    <div class="queue-item-info">
                        <div class="queue-item-title" title="${escapeHtml(song.title)}">${escapeHtml(song.title)}</div>
                        <div class="queue-item-artist">${escapeHtml(song.artist || '')}</div>
                    </div>
                    <button class="queue-item-remove" data-idx="${i}" title="从播放列表移除">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                    </button>
                </div>`;
        }).join('');

        // 绑定点击：点击歌曲切到该曲
        listEl.querySelectorAll('.queue-item').forEach(item => {
            item.addEventListener('click', (e) => {
                if (e.target.closest('.queue-item-remove')) return;
                const idx = parseInt(item.dataset.index);
                if (idx !== this.currentIndex) {
                    this.currentIndex = idx;
                    this.play(this.playlist[idx]);
                } else {
                    this.togglePlay();
                }
            });
        });

        // 绑定移除按钮
        listEl.querySelectorAll('.queue-item-remove').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const idx = parseInt(btn.dataset.idx);
                this.playlist.splice(idx, 1);
                if (idx === this.currentIndex) {
                    // 移除正在播放的 → 自动播放下一首
                    if (this.playlist.length > 0) {
                        this.currentIndex = Math.min(idx, this.playlist.length - 1);
                        this.play(this.playlist[this.currentIndex]);
                    } else {
                        this.currentIndex = -1;
                        this.currentSong = null;
                        this.pause();
                    }
                } else if (idx < this.currentIndex) {
                    this.currentIndex--;
                }
                this.updateQueueList();
            });
        });
    },

    // ========== 循环模式切换 ==========
    cycleLoopMode() {
        const modes = ['list', 'single', 'shuffle'];
        const idx = modes.indexOf(this.loopMode);
        this.loopMode = modes[(idx + 1) % modes.length];
        this.updateLoopBtn();
        // 🟢 切换到 shuffle 时重置洗牌列表
        if (this.loopMode === 'shuffle') {
            this.shufflePlaylist = [];
            this.shuffleIndex = 0;
        }
        this.showToast(
            this.loopMode === 'list' ? '🔁 列表循环' :
            this.loopMode === 'single' ? '🔂 单曲循环' : '🔀 随机播放',
            'info'
        );
    },

    updateLoopBtn() {
        const btn = document.getElementById('mode-btn');
        if (!btn) return;
        if (this.loopMode === 'list') {
            btn.textContent = '🔁';
            btn.className = 'mode-btn';
        } else if (this.loopMode === 'single') {
            btn.textContent = '🔂';
            btn.className = 'mode-btn active';
        } else {
            btn.textContent = '🔀';
            btn.className = 'mode-btn active';
        }
    },

    // 播放结束时根据模式处理
    onEnded() {
        if (this.loopMode === 'single') {
            // 单曲循环：重新播放
            this.audio.currentTime = 0;
            this.audio.play().catch(() => {});
            return;
        }
        if (this.loopMode === 'shuffle') {
            this.shuffleNext();
            return;
        }
        // 列表循环
        this.next();
    },

    // 洗牌模式：shuffleIndex 始终指向下一首要播的歌
    shuffleNext() {
        if (this.playlist.length === 0) return;
        // 播完一遍或刚进入 shuffle → 重新洗牌
        if (this.shufflePlaylist.length === 0 || this.shuffleIndex >= this.shufflePlaylist.length) {
            this.shufflePlaylist = this._doShuffle(this.playlist);
            this.shuffleIndex = 0;
        }
        const song = this.shufflePlaylist[this.shuffleIndex];
        this.shuffleIndex++; // 移到下一首待播位置
        this.currentIndex = this.playlist.indexOf(song);
        this.play(song);
    },

    shufflePrev() {
        if (this.playlist.length === 0) return;
        // 还没开始 → 生成列表从末尾开始
        if (this.shufflePlaylist.length === 0 || this.shuffleIndex === 0) {
            this.shufflePlaylist = this._doShuffle(this.playlist);
            this.shuffleIndex = this.shufflePlaylist.length - 1;
        } else {
            this.shuffleIndex--; // 退到上一首
        }
        const song = this.shufflePlaylist[this.shuffleIndex];
        this.currentIndex = this.playlist.indexOf(song);
        this.play(song);
    },

    /** Fisher-Yates 洗牌（比 sort(() => Math.random() - 0.5) 更均匀） */
    _doShuffle(arr) {
        const result = [...arr];
        for (let i = result.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [result[i], result[j]] = [result[j], result[i]];
        }
        return result;
    },

    /**
     * 进度条自动隐藏：鼠标悬停时显示，离开3秒后自动隐藏
     */
    initProgressAutoHide() {
        const player = document.getElementById('music-player');
        const progress = player?.querySelector('.player-progress');
        if (!player || !progress) return;

        let hideTimer = null;

        const showProgress = () => {
            if (hideTimer) {
                clearTimeout(hideTimer);
                hideTimer = null;
            }
            progress.classList.remove('progress-hidden');
        };

        const startHideTimer = () => {
            if (hideTimer) clearTimeout(hideTimer);
            hideTimer = setTimeout(() => {
                // 仅当有歌曲播放时才隐藏
                if (this.currentSong) {
                    progress.classList.add('progress-hidden');
                }
            }, 3000);
        };

        // 鼠标进入播放器 → 立即显示
        player.addEventListener('mouseenter', showProgress);

        // 鼠标离开播放器 → 3秒后隐藏
        player.addEventListener('mouseleave', startHideTimer);

        // 鼠标在播放器内移动 → 重新计时
        player.addEventListener('mousemove', () => {
            if (progress.classList.contains('progress-hidden')) {
                showProgress();
            }
            // 重置计时器
            if (hideTimer) {
                clearTimeout(hideTimer);
            }
            if (this.currentSong) {
                hideTimer = setTimeout(() => {
                    progress.classList.add('progress-hidden');
                }, 3000);
            }
        });

        // 初始状态：无歌曲时隐藏进度条
        progress.classList.add('progress-hidden');

        // 暴露方法供 playSong 调用
        this._showProgress = showProgress;
        this._hideProgress = () => progress.classList.add('progress-hidden');
    },

    play(song) {
        if (!song) return;

        this.currentSong = song;
        this.isPlaying = true;

        // 开始播放时显示进度条
        if (this._showProgress) this._showProgress();

        // 更新播放器UI
        document.querySelector('.player-cover img').src = song.coverUrl || '/images/default-cover.svg';
        document.querySelector('.player-song').textContent = song.title;
        document.querySelector('.player-artist').textContent = song.artist;

        // 同步喜欢按钮状态
        this.updateLikeBtn(this.likedSongs.has(song.id));

        // 更新播放列表面板
        if (typeof this.updateQueueList === 'function') this.updateQueueList();

        // 尝试播放音频（如果有audio_url）
        if (song.audioUrl) {
            this.audio.src = song.audioUrl;
            this.audio.play().catch(() => {
                // 如果没有实际音频文件，只做UI展示
                this.simulatePlayback();
            });
        } else {
            this.simulatePlayback();
        }

        // 更新播放按钮
        this.updatePlayButton();

        // 🟢 P3-7: 持久化当前播放状态
        this._saveToStorage();

        // 🟢 发送播放量统计（keepalive:true 防止切歌/跳转时请求被 abort）
        if (song.id) {
            fetch(`/song/api/play/${song.id}`, { method: 'POST', credentials: 'same-origin', keepalive: true })
                .catch(() => {});
        }
    },

    simulatePlayback() {
        // 模拟播放进度（用于演示）
        // 注意：audio.duration 是只读属性，且无真实音频时设置 currentTime 不触发 timeupdate
        // 所以这里直接用独立属性 + 直接操作 DOM
        this.simulated = { startTime: Date.now(), duration: 180 }; // 模拟3分钟

        const step = () => {
            if (!this.isPlaying) return;
            const s = this.simulated;
            const elapsed = (Date.now() - s.startTime) / 1000;
            if (elapsed >= s.duration) {
                this.simulated = null;
                this.next();
                return;
            }
            const percent = (elapsed / s.duration) * 100;
            // 直接更新 DOM（因为没有 timeupdate 事件）
            if (this._progressFill) this._progressFill.style.width = `${percent}%`;
            if (this._progressThumb) this._progressThumb.style.left = `${percent}%`;
            if (this._currentTime) this._currentTime.textContent = this.formatTime(elapsed);
            requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
    },

    togglePlay() {
        if (this.isPlaying) {
            this.pause();
        } else {
            this.resume();
        }
    },

    pause() {
        this.isPlaying = false;
        this.audio.pause();
        // 🟢 模拟播放：记录当前已播放时间，供 resume 时继续
        if (this.simulated) {
            this.simulated.pausedAt = (Date.now() - this.simulated.startTime) / 1000;
        }
        this.updatePlayButton();
        // 暂停后保持进度条可见，鼠标离开后仍会按计时器自动隐藏
    },

    resume() {
        if (!this.currentSong) return;
        this.isPlaying = true;
        // 🟢 有模拟播放状态 → 从暂停处继续
        if (this.simulated && this.simulated.pausedAt) {
            this.simulated.startTime = Date.now() - this.simulated.pausedAt * 1000;
            delete this.simulated.pausedAt;
            const dur = this.simulated.duration;
            const step = () => {
                if (!this.isPlaying) return;
                const elapsed = (Date.now() - this.simulated.startTime) / 1000;
                if (elapsed >= dur) { this.simulated = null; this.next(); return; }
                const percent = (elapsed / dur) * 100;
                if (this._progressFill) this._progressFill.style.width = `${percent}%`;
                if (this._progressThumb) this._progressThumb.style.left = `${percent}%`;
                if (this._currentTime) this._currentTime.textContent = this.formatTime(elapsed);
                requestAnimationFrame(step);
            };
            requestAnimationFrame(step);
        } else {
            this.audio.play().catch(() => {});
        }
        this.updatePlayButton();
        // 恢复播放时重新显示进度条
        if (this._showProgress) this._showProgress();
    },

    next() {
        if (this.playlist.length === 0) return;
        if (this.loopMode === 'shuffle') {
            this.shuffleNext();
            return;
        }
        this.currentIndex = (this.currentIndex + 1) % this.playlist.length;
        this.play(this.playlist[this.currentIndex]);
    },

    prev() {
        if (this.playlist.length === 0) return;
        if (this.loopMode === 'shuffle') {
            this.shufflePrev();
            return;
        }
        this.currentIndex = (this.currentIndex - 1 + this.playlist.length) % this.playlist.length;
        this.play(this.playlist[this.currentIndex]);
    },

    // ========== 喜欢当前歌曲 ==========
    toggleLikeCurrent() {
        if (!this.currentSong || !this.currentSong.id) return;
        const songId = this.currentSong.id;
        const isLiked = this.likedSongs.has(songId);

        fetch(`/song/api/like/${songId}`, { method: 'POST', credentials: 'same-origin' })
            .then(r => r.json())
            .then(data => {
                if (data.success !== false) {
                    if (isLiked) {
                        this.likedSongs.delete(songId);
                        localStorage.setItem('likedSongs', JSON.stringify([...this.likedSongs]));
                        this.updateLikeBtn(false);
                        this.showToast('已取消喜欢 💔', 'info');
                    } else {
                        this.likedSongs.add(songId);
                        localStorage.setItem('likedSongs', JSON.stringify([...this.likedSongs]));
                        this.updateLikeBtn(true);
                        this.showToast('已添加到喜欢 ❤️', 'success');
                    }
                }
            })
            .catch(() => {});
    },

    updateLikeBtn(liked) {
        const btn = document.getElementById('player-like-btn');
        if (!btn) return;
        if (liked) {
            btn.textContent = '❤️';
            btn.className = 'player-like-btn liked';
        } else {
            btn.textContent = '🤍';
            btn.className = 'player-like-btn';
        }
    },

    // ========== Toast 通知 ==========
    showToast(msg, type) {
        let container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            document.body.appendChild(container);
        }
        const toast = document.createElement('div');
        toast.className = 'toast toast-' + (type || 'info');
        toast.textContent = msg;
        container.appendChild(toast);

        // 动画进入后自动移除
        requestAnimationFrame(() => {
            toast.style.opacity = '1';
            toast.style.transform = 'translateY(0)';
        });
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(-10px)';
            setTimeout(() => toast.remove(), 300);
        }, 2500);
    },

    // ========== 全局喜欢辅助方法 ==========
    toggleLikeLocal(songId) {
        const id = String(songId);
        if (this.likedSongs.has(id)) {
            this.likedSongs.delete(id);
        } else {
            this.likedSongs.add(id);
        }
        localStorage.setItem('likedSongs', JSON.stringify([...this.likedSongs]));
        return this.likedSongs.has(id);
    },

    isLiked(songId) {
        return this.likedSongs.has(String(songId));
    },

    syncLikeBtn(song) {
        const btn = document.querySelector('.player-like-btn');
        if (!btn || !song) return;
        const liked = song && song.id != null && this.likedSongs.has(String(song.id));
        this.updateLikeBtn(liked);
    },

    updateProgress() {
        // 模拟播放模式不触发这里（simulatePlayback 直接操作 DOM）
        if (this.simulated) return;

        const currentTime = this.audio.currentTime || 0;
        const duration = this.audio.duration || 180;
        const progress = (currentTime / duration) * 100;

        if (this._progressFill) this._progressFill.style.width = `${progress}%`;
        if (this._progressThumb) this._progressThumb.style.left = `${progress}%`;
        if (this._currentTime) this._currentTime.textContent = this.formatTime(currentTime);
    },

    updatePlayButton() {
        const btn = document.querySelector('.play-btn');
        btn.textContent = this.isPlaying ? '⏸' : '▶';
    },

    formatTime(seconds) {
        const m = Math.floor(seconds / 60);
        const s = Math.floor(seconds % 60);
        return `${m}:${s.toString().padStart(2, '0')}`;
    },

    setVolume(vol) {
        this.volume = vol;
        this.audio.volume = vol;
        document.querySelector('.volume-fill').style.width = `${vol * 100}%`;
    },

    seekTo(percent) {
        const duration = this.audio.duration || 180;
        this.audio.currentTime = (percent / 100) * duration;
    }
};

// ========== 页面初始化 ==========
document.addEventListener('DOMContentLoaded', () => {
    Player.init();

    // 绑定播放/暂停按钮
    document.querySelectorAll('.play-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            Player.togglePlay();
        });
    });

    // 绑定进度条点击 + 悬停预览
    const progressBar = document.querySelector('.progress-bar');
    if (progressBar) {
        // 点击跳转
        progressBar.addEventListener('click', (e) => {
            const rect = progressBar.getBoundingClientRect();
            const percent = ((e.clientX - rect.left) / rect.width) * 100;
            Player.seekTo(percent);
        });
        // 悬停显示预览时间
        const previewEl = document.createElement('span');
        previewEl.className = 'progress-preview';
        previewEl.style.cssText = 'position:absolute;top:-20px;font-size:11px;color:#a78bfa;display:none;';
        progressBar.appendChild(previewEl);
        progressBar.addEventListener('mousemove', (e) => {
            const rect = progressBar.getBoundingClientRect();
            const pct = ((e.clientX - rect.left) / rect.width) * 100;
            const dur = Player.audio.duration || 180;
            const time = Player.formatTime((pct / 100) * dur);
            previewEl.textContent = time;
            previewEl.style.left = `calc(${pct}% - 12px)`;
            previewEl.style.display = 'block';
        });
        progressBar.addEventListener('mouseleave', () => {
            previewEl.style.display = 'none';
        });
    }

    // 绑定音量条（点击 + 拖动）
    const volumeBar = document.querySelector('.volume-bar');
    if (volumeBar) {
        const setVolFromEvent = (e) => {
            const rect = volumeBar.getBoundingClientRect();
            const vol = (e.clientX - rect.left) / rect.width;
            Player.setVolume(Math.max(0, Math.min(1, vol)));
        };
        volumeBar.addEventListener('click', setVolFromEvent);
        // 拖动调节音量
        let dragging = false;
        volumeBar.addEventListener('mousedown', (e) => {
            dragging = true;
            setVolFromEvent(e);
            document.addEventListener('mousemove', onMove);
            document.addEventListener('mouseup', () => {
                dragging = false;
                document.removeEventListener('mousemove', onMove);
            }, { once: true });
        });
        function onMove(e) {
            if (!dragging) return;
            setVolFromEvent(e);
        }
    }

    // 绑定上一首/下一首
    document.querySelector('.prev-btn')?.addEventListener('click', () => Player.prev());
    document.querySelector('.next-btn')?.addEventListener('click', () => Player.next());

    // 绑定循环模式切换
    document.getElementById('mode-btn')?.addEventListener('click', () => Player.cycleLoopMode());

    // 绑定播放器喜欢按钮
    document.getElementById('player-like-btn')?.addEventListener('click', () => Player.toggleLikeCurrent());

    // 绑定播放列表面板开关
    const queueBtn = document.getElementById('queue-btn');
    const queuePanel = document.getElementById('queue-panel');
    const queueClose = document.getElementById('queue-close');
    if (queueBtn && queuePanel) {
        queueBtn.addEventListener('click', () => {
            queuePanel.classList.toggle('open');
            queueBtn.classList.toggle('active');
            if (queuePanel.classList.contains('open')) Player.updateQueueList();
        });
        queueClose?.addEventListener('click', () => {
            queuePanel.classList.remove('open');
            queueBtn.classList.remove('active');
        });
    }

    // 快捷键提示：首次播放时显示，3秒后淡出
    const tipEl = document.getElementById('shortcuts-tip');
    if (tipEl && !localStorage.getItem('shortcutsTipDismissed')) {
        setTimeout(() => {
            tipEl.classList.add('show');
            setTimeout(() => {
                tipEl.classList.remove('show');
                localStorage.setItem('shortcutsTipDismissed', '1');
            }, 5000);
        }, 3000);
    }

    // 绑定歌曲卡片点击播放
    document.querySelectorAll('.song-card').forEach(card => {
        card.addEventListener('click', (e) => {
            // 如果点击的是按钮（喜欢/添加），不触发播放
            if (e.target.closest('.mini-btn') || e.target.closest('.btn-like')) return;
            const songJson = card.getAttribute('data-song');
            if (songJson) {
                try {
                    const song = JSON.parse(songJson);
                    playSong(song);
                } catch (err) {
                    console.warn('解析歌曲数据失败', err);
                }
            }
        });
    });
});

// ========== 播放歌曲 ==========
function playSong(song) {
    // 设置播放列表
    Player.playlist = window.currentPlaylist || [song];
    Player.currentIndex = Player.playlist.findIndex(s => s.id === song.id);
    if (Player.currentIndex === -1) {
        Player.playlist = [song];
        Player.currentIndex = 0;
    }
    Player.play(song);

    // 更新外部播放按钮
    updateExternalPlayBtn(song);
}

// ========== 外部播放（跳转网易云音乐） ==========
function updateExternalPlayBtn(song) {
    const externalBtns = document.querySelectorAll('.external-play-btn');
    let url = buildExternalUrl(song);
    externalBtns.forEach(btn => {
        btn.href = url;
        btn.style.display = 'inline-flex';
    });
}

/** 构造外部播放 URL（网易云音乐搜索） */
function buildExternalUrl(song) {
    if (!song) return 'https://music.163.com/';
    if (song.externalUrl) return song.externalUrl;
    var keyword = (song.title || '') + ' ' + (song.artist || '');
    return 'https://music.163.com/#/search/m/?s=' + encodeURIComponent(keyword.trim());
}

/**
 * 安全的跨域跳转：用临时 <a> 模拟点击，绕过弹窗拦截器
 * 必须将元素加入 DOM，否则浏览器会忽略 .click()
 */
function safeOpenUrl(url) {
    if (!url || url === '#' || url === '') return;
    var a = document.createElement('a');
    a.href = url;
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    // 等事件循环处理完再移除
    setTimeout(function() { document.body.removeChild(a); }, 100);
}

/**
 * 跳转网易云音乐搜索
 */
function externalPlayDirect(btn) {
    var title = btn.getAttribute('data-title') || '';
    var artist = btn.getAttribute('data-artist') || '';
    var keyword = (title + ' ' + artist).trim();
    if (!keyword) {
        showMessage('message-area', '暂无歌曲信息', 'error');
        return;
    }
    var url = 'https://music.163.com/#/search/m/?s=' + encodeURIComponent(keyword);
    console.log('[外部播放] 跳转至:', url);
    window.location.href = url;
}

function externalPlay(song) {
    safeOpenUrl(buildExternalUrl(song));
}

// 从按钮 data 属性获取歌曲信息进行外部播放
function externalPlayFromBtn(btn) {
    var song = {
        id: btn.getAttribute('data-song-id'),
        title: btn.getAttribute('data-song-title'),
        artist: btn.getAttribute('data-song-artist')
    };
    externalPlay(song);
}

// ========== 搜索功能 ==========
function searchSongs() {
    const keyword = document.querySelector('.search-box input').value.trim();
    if (keyword) {
        window.location.href = `/song/search?keyword=${encodeURIComponent(keyword)}`;
    }
}

// ========== 消息提示 ==========
function showMessage(elementId, message, type) {
    const el = document.getElementById(elementId);
    if (!el) return;
    el.textContent = message;
    el.className = `message show message-${type}`;
    setTimeout(() => {
        el.classList.remove('show');
    }, 3000);
}

// ========== 歌单相关 API ==========
async function addToPlaylist(songId, playlistId) {
    try {
        const formData = new URLSearchParams();
        formData.append('songId', songId);
        formData.append('playlistId', playlistId);

        const response = await fetch('/playlist/api/add-song', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData,
            credentials: 'same-origin'
        });
        const data = await response.json();
        if (data.success) {
            showMessage('message-area', '已添加到歌单', 'success');
        } else {
            showMessage('message-area', data.message || '添加失败', 'error');
        }
    } catch (err) {
        showMessage('message-area', '操作失败，请稍后重试', 'error');
    }
}

async function removeFromPlaylist(event, btn) {
    event.stopPropagation();
    var songId = btn.getAttribute('data-song-id');
    if (!songId) return;
    var playlistId = PLAYLIST_ID || new URLSearchParams(window.location.search).get('playlistId');
    try {
        const formData = new URLSearchParams();
        formData.append('songId', songId);
        formData.append('playlistId', playlistId);

        const response = await fetch('/playlist/api/remove-song', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData,
            credentials: 'same-origin'
        });
        const data = await response.json();
        if (data.success) {
            location.reload();
        }
    } catch (err) {
        showMessage('message-area', '操作失败', 'error');
    }
}

// ========== 创建歌单 ==========
async function createPlaylist() {
    const name = document.getElementById('playlist-name')?.value.trim();
    const description = document.getElementById('playlist-desc')?.value.trim();

    if (!name) {
        showMessage('message-area', '请输入歌单名称', 'error');
        return;
    }

    try {
        const formData = new URLSearchParams();
        formData.append('name', name);
        formData.append('description', description || '');

        const response = await fetch('/playlist/api/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData,
            credentials: 'same-origin'
        });
        const data = await response.json();
        if (data.success) {
            window.location.href = `/playlist/${data.playlistId}`;
        } else {
            showMessage('message-area', data.message || '创建失败', 'error');
        }
    } catch (err) {
        showMessage('message-area', '创建失败，请稍后重试', 'error');
    }
}

// ========== 删除歌单 ==========
async function deletePlaylist(playlistId) {
    if (!confirm('确定要删除这个歌单吗？')) return;

    try {
        const response = await fetch(`/playlist/api/delete/${playlistId}`, {
            method: 'POST',
            credentials: 'same-origin'
        });
        const data = await response.json();
        if (data.success) {
            window.location.href = '/playlist/mine';
        }
    } catch (err) {
        showMessage('message-area', '删除失败', 'error');
    }
}

// ========== 更新个人资料 ==========
async function updateProfile() {
    const formEl = document.getElementById('profile-form');
    if (!formEl) {
        showMessage('message-area', '表单不存在', 'error');
        return;
    }
    const formData = new FormData(formEl);
    const params = new URLSearchParams();

    formData.forEach((value, key) => {
        params.append(key, value);
    });

    try {
        const response = await fetch('/profile/api/update', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params,
            credentials: 'same-origin'
        });
        const data = await response.json();
        if (data.success) {
            showMessage('message-area', '更新成功', 'success');
        } else {
            showMessage('message-area', data.message || '更新失败', 'error');
        }
    } catch (err) {
        showMessage('message-area', '更新失败，请稍后重试', 'error');
    }
}

// ========== 播放器扩展已整合到 Player 对象中 ==========
// (已迁移: 循环模式 next/prev, 键盘快捷键, Toast, 音量拖动, 队列面板)

// ========== 工具函数 ==========
function escapeHtml(str) {
    if (str == null) return '';
    return String(str).replace(/[&<>"']/g, ch => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[ch]));
}

// ========== 全局喜欢按钮 ==========
/**
 * 切换喜欢状态
 * 调用方式（兼容两种参数顺序）：
 *   toggleLike(btn, songId, event?)  — 元素在前
 *   toggleLike(songId, btn, event?)  — ID 在前
 */
function toggleLike(arg1, arg2, arg3) {
    // 统一解析参数
    let songId, btn, evt;
    if (typeof arg1 === 'object' && arg1 !== null && arg1.getAttribute) {
        // toggleLike(btn, songId, evt)
        btn = arg1;
        songId = arg2 ?? btn.getAttribute('data-song-id') ?? btn.getAttribute('data-like-id');
        evt = arg3;
    } else {
        // toggleLike(songId, btn, evt)
        songId = arg1;
        btn = arg2;
        evt = arg3;
    }
    if (!songId) return;

    // 阻止事件冒泡（优先用显式传入的 evt）
    if (evt && evt.stopPropagation) evt.stopPropagation();

    const liked = Player.toggleLikeLocal(songId);
    // 更新所有相关按钮的视觉状态
    const selector = `[data-song-id="${songId}"], [data-like-id="${songId}"]`;
    document.querySelectorAll(selector).forEach(el => {
        el.classList.toggle('liked', liked);
        if (el.classList.contains('mini-btn')) {
            el.textContent = liked ? '❤' : '♡';
        }
    });
    // 同步播放器喜欢按钮
    if (Player.currentSong && String(Player.currentSong.id) === String(songId)) {
        Player.syncLikeBtn(Player.currentSong);
    }

    // 调用API并更新喜欢数
    fetch(`/song/api/like/${songId}`, { method: 'POST', credentials: 'same-origin' })
        .then(r => r.json())
        .then(data => {
            if (data.likeCount !== undefined) {
                // 更新所有显示喜欢数的元素
                document.querySelectorAll('.stat .val').forEach(el => {
                    const label = el.closest('.stat')?.querySelector('.label');
                    if (label && label.textContent.includes('喜欢')) {
                        el.textContent = Number(data.likeCount).toLocaleString();
                    }
                });
            }
        })
        .catch(() => {});
}

// ========== 初始化喜欢按钮视觉状态 ==========
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => {
        document.querySelectorAll('[data-like-id]').forEach(btn => {
            const id = btn.getAttribute('data-like-id');
            if (id && Player.isLiked(id)) {
                btn.classList.add('liked');
                if (btn.classList.contains('mini-btn')) btn.textContent = '❤';
            }
        });
    }, 100);
});

// ========== 随机播放推荐列表 ==========
function playRandomRecommended() {
    if (!window.currentPlaylist || window.currentPlaylist.length === 0) {
        Player.showToast('暂无可播放的推荐歌曲', 'error');
        return;
    }
    const list = [...window.currentPlaylist];
    for (let i = list.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [list[i], list[j]] = [list[j], list[i]];
    }
    Player.playlist = list;
    Player.loopMode = 'shuffle';
    Player.currentIndex = 0;
    Player.play(list[0]);
    Player.updateLoopBtn();
    Player.showToast('🎲 已开启随机播放', 'success');
}

// ========== 提交评论 ==========
async function submitComment(form) {
    let songId, content;
    if (typeof form === 'object' && form !== null) {
        // 表单提交模式
        const formData = new FormData(form);
        songId = formData.get('songId');
        content = formData.get('content');
    } else {
        // 直接传参模式（兼容旧调用）
        songId = form;
        const textarea = document.getElementById('comment-input');
        content = textarea ? textarea.value.trim() : '';
    }
    if (!content || !content.trim()) {
        showMessage('message-area', '评论内容不能为空', 'error');
        return;
    }
    try {
        const params = new URLSearchParams();
        params.append('songId', songId);
        params.append('content', content.trim());
        const res = await fetch('/song/api/comment', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params,
            credentials: 'same-origin'
        });
        const data = await res.json().catch(() => ({}));
        if (data.success) {
            showMessage('message-area', '评论发布成功', 'success');
            setTimeout(() => location.reload(), 800);
        } else {
            showMessage('message-area', data.message || '评论失败', 'error');
        }
    } catch (e) {
        showMessage('message-area', '评论失败，请稍后重试', 'error');
    }
}

// ========== 加入歌单弹窗占位 ==========
function showAddToPlaylist(songId) {
    const playlistId = prompt('请输入歌单ID（演示版）', '1');
    if (playlistId) addToPlaylist(songId, playlistId);
}