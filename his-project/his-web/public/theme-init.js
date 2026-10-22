// 主题初始化：防深色模式刷新闪白（必须在 CSS 加载前执行）
;(function () {
  try {
    if (localStorage.getItem('his_theme') === 'dark') {
      document.documentElement.classList.add('dark')
    }
  } catch (e) {
    /* 隐私模式下 localStorage 不可用，按浅色处理 */
  }
})()
