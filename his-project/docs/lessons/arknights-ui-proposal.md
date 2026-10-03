# 明日方舟风格 UI 改造——调研报告与技术方案

> 来源：GitHub 开源复刻项目 / Game UI Database / NGA 论坛字体讨论 / Juejin 前端复刻教程 / CSS 动画案例
> 用途：HIS 前端 UI 全面改造为明日方舟风格

---

## 一、调研成果

### 1.1 色彩体系（从开源复刻 + Game UI Database 提取）

| 用途 | 色值 | 来源 |
|---|---|---|
| 页面底色 | `#141414` ~ `#1b1b1b` | arknights-h5 CSS / Game UI Database 截图 |
| 卡片/面板 | `#222222` ~ `#282828` | 同上 |
| 侧边栏 | `#0d0d0d` | ArkTheme 项目 |
| 强调橙 | `#ff5e19` / `#e8a040` | arknights-h5 / 社区共识 |
| 罗德岛蓝（辅助） | `#05a7dc` | arknights-h5 CSS `border-image` |
| 主文字 | `#e0e0e0` / `#e9e9e9` | arknights-h5 / 截图取样 |
| 次级文字 | `#909090` / `#989898` | 同上 |
| 边框 | `#333333` / `#454545` | arknights-h5 |
| 危险/紧急 | `#e84749` | 截图取样 |
| 渐变边框 | `linear-gradient(#05a7dc, #454545, #05a7dc)` | arknights-h5 CSS |

### 1.2 字体（NGA 论坛社区共识 + arknights-h5 实际使用）

| 用途 | 官方/近似 | 免费替代 |
|---|---|---|
| 英文标题/数字 | 定制字体（近似 Futura/Helvetica 几何无衬线） | **Rajdhani**（免费可商用，最常被社区推荐）或 HarmonyOS Sans |
| 中文正文 | 定制黑体 | **Noto Sans SC**（arknights-h5 实际使用） |
| 等宽/代码 | — | Consolas / JetBrains Mono |

### 1.3 动画时序（从游戏录屏逐帧分析）

| 属性 | 值 | 来源 |
|---|---|---|
| 对话框弹出 | **150~250ms** | 录屏逐帧计数 |
| 缓动曲线（进入） | `cubic-bezier(0.2, 0, 0.2, 1)` 或 `cubic-bezier(0.25, 0.46, 0.45, 0.94)` | 快启动/强落定，与游戏观感匹配 |
| 缓动曲线（退出） | `cubic-bezier(0.4, 0, 1, 1)` | ease-in 系列 |
| 页面切换 | **250~300ms** | 从右滑入 + 渐变 |
| 悬停扫入 | **250ms** ease-out | 背景从左到右填充 |
| 列表交错 | **30ms/项** | 每项递增延迟 |

> 来源：Juejin 前端复刻教程 / YouTube "why the Arknights UI design is GOATED" 逐帧分析 / cubic-bezier.com 对照

### 1.4 关键开源参考

| 项目 | 星/状态 | 可提取 |
|---|---|---|
| [misaka10843/arknights-h5](https://github.com/misaka10843/arknights-h5) | archived, MIT | 色值/字体/布局/贴图定位 |
| [Mashiro/arknights-ui](https://github.com/Mashiro/arknights-ui) | ~94★, CSS-only | 纯 CSS 主界面复刻，透视菜单 |
| [BetterNCM/ArkTheme](https://github.com/BetterNCM/ArkTheme) | active | Arknights 风格主题（NCM 播放器） |
| [Game UI Database](https://www.gameuidatabase.com) | 参考 | 1300+ 游戏 55000+ UI 截图，可按颜色/布局筛选 |
| [Juejin 前端复刻教程](https://juejin.cn/post/6844904148337426440) | 教程 | Logo 粒子动画 Canvas 实现 |

### 1.5 设计语言核心原则（从分析文章提炼）

1. **信息密度高但层次分明**——每个像素都有功能
2. **直角/切角代替圆角**——传达"精密工具"而非"消费应用"
3. **单色强调**——全局只有一种强调色（琥珀橙），用亮度/透明度做层次
4. **工业感装饰**——斜纹、扫描线、技术括号，但不喧宾夺主
5. **动画快而干脆**——150~250ms，无弹跳/缓动过长，"工具的手感"
6. **暗色不等于低对比**——文字对比度 12:1（WCAG AAA），但色彩饱和度低

---

## 二、HIS 改造方案（基于调研修订）

### 2.1 设计令牌（从调研数据推导）

```css
:root {
  /* 底色 */
  --ak-bg-root: #141414;
  --ak-bg-page: #1b1b1b;
  --ak-bg-card: #222222;
  --ak-bg-card-alt: #282828;
  --ak-bg-input: #2a2a2a;
  --ak-bg-hover: #333333;
  --ak-aside-bg: #0d0d0d;
  --ak-header-bg: #181818;

  /* 主色：琥珀橙（调研确认的最佳强调色） */
  --ak-primary: #e8a040;
  --ak-primary-light: #ffc069;
  --ak-primary-dim: rgba(232, 160, 64, 0.12);
  --ak-primary-stripe: rgba(232, 160, 64, 0.04);

  /* 文字 */
  --ak-text: #e0e0e0;
  --ak-text-sec: #909090;
  --ak-text-dim: #606060;

  /* 边框 */
  --ak-border: #333333;
  --ak-border-light: #2a2a2a;

  /* 语义色 */
  --ak-danger: #e84749;
  --ak-success: #5cb85c;
  --ak-warning: #e8a040;
  --ak-info: #5b9bd5;

  /* 字体 */
  --ak-font: 'Rajdhani', 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  --ak-font-mono: 'Rajdhani', 'Consolas', monospace;

  /* 几何 */
  --ak-radius: 0px;
  --ak-cut: 6px;

  /* 动画 */
  --ak-ease: cubic-bezier(0.2, 0, 0.2, 1);
  --ak-ease-out: cubic-bezier(0.25, 0.46, 0.45, 0.94);
  --ak-ease-in: cubic-bezier(0.4, 0, 1, 1);
  --ak-dur-fast: 150ms;
  --ak-dur: 250ms;
  --ak-dur-slow: 300ms;
}
```

### 2.2 动画规范（从调研时序推导）

| 交互 | 时长 | 缓动 | 属性 |
|---|---|---|---|
| 对话框弹入 | 200ms | ease-out (快起强落) | scale + translateY |
| 对话框关闭 | 150ms | ease-in | scale + opacity |
| 页面切换 | 250ms | ease-out | translateX + opacity |
| 按钮悬停扫入 | 250ms | ease-out | background-size |
| 列表交错入场 | 300ms + 30ms/项 | ease-out | translateX + opacity |
| Toast 滑入 | 300ms | 微弹 | translateX + translateY |
| 菜单指示条滑动 | 250ms | ease-out | top |
| 扫线 | 600ms | ease-out | top + opacity |
| 按钮按下 | 80ms | linear | scale + brightness |

### 2.3 需要新增的文件

| 文件 | 内容 |
|---|---|
| `styles.css` | 完全重写（~600 行），全局令牌 + EP 覆盖 + 全部动画 |
| `composables/useCountUp.ts` | KPI 数字滚动 |
| `index.html` | `<html class="dark">` + Rajdhani 字体 CDN |
| `Layout.vue` | +路由过渡 +菜单指示条滑动 +暗色 class |

### 2.4 44 个页面组件

零改动——全局 CSS 变量和 EP 组件覆盖自动生效。

### 2.5 交付顺序

| 步骤 | 内容 | 验证 |
|---|---|---|
| 1 | styles.css 重写 + index.html dark class + 字体 | 构建 → 全站截图 |
| 2 | Layout.vue 过渡 + 菜单指示条 | 页面切换录屏 |
| 3 | useCountUp + KPI 看板数字动画 | KPI 页录屏 |
| 4 | 全页面走查 | 44 页逐页检查 |
| 5 | 微调 | 截图对比 |

### 2.6 不动的

- 业务逻辑零改动
- API 零改动
- 44 个页面组件的 template/script 零改动（纯 CSS 层变更）
- Element Plus 组件结构零改动

---

## 三、参考来源

- [Game UI Database — Arknights](https://www.gameuidatabase.com) — UI 截图参考
- [misaka10843/arknights-h5](https://github.com/misaka10843/arknights-h5) — CSS 色值/字体提取
- [Mashiro/arknights-ui](https://github.com/Mashiro/arknights-ui) — 纯 CSS 主界面复刻
- [BetterNCM/ArkTheme](https://github.com/BetterNCM/ArkTheme) — Arknights 风格主题
- [Juejin 前端复刻教程](https://juejin.cn/post/6844904148337426440) — Canvas 粒子动画
- [NGA 字体讨论帖](https://bbs.nga.cn) — Futura/Helvetica/鸿蒙字体共识
- [cubic-bezier.com](https://cubic-bezier.com/) — 缓动曲线对照工具
