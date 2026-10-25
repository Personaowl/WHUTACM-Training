# 全局样式模块

`../index.css` 是唯一全局入口。这里只拆分原文件的连续区段，不改变选择器、声明、断点、动画或导入顺序；页面和组件原有 CSS 继续按各自入口加载。

顺序是级联约定，不能按文件名排序：

1. `foundation.css`：Tailwind 配置、主题、间距工具、变量和基础重置。
2. `controls.css`：表单、按钮、题目卡片等基础控件。
3. `app-shell.css`、`sidebar.css`、`topbar.css`：应用布局及导航。
4. `surfaces.css`、`profile.css`、`heatmap.css`：内容容器、资料和热力图。
5. `authentication.css`：登录/注册的基础布局与表单。
6. `responsive.css`：原位置的移动端和减少动态效果规则。
7. `typography.css`：共享字号和控件尺寸覆盖，包含门户弹窗。
8. `binding.css`：账号绑定的基础样式。
9. `responsive-overrides.css`：最后生效的小屏字号、绑定/热力图及桌面注册表单规则。

同一选择器可能先定义基础值，后定义字号或响应式覆盖。这类重复不能仅凭名称删除。修改时优先定位规则的原职责，避免增加新的全局覆盖层。

验证：`npm test` 检查导入顺序、源码约束和生产 CSS；`npm run build` 检查打包。视觉变化还需检查电脑、手机、横屏、键盘焦点和减少动态效果。本次拆分前后的生产 CSS 语法树已逐项比对，规则、声明及其顺序一致。
