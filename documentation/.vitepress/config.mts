import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: "Pragmatic DDD",
  description: "Pragmatic DDD 是一个以\"务实\"为设计哲学的领域驱动设计（DDD）框架",
  ignoreDeadLinks: true,
  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
      logo: '/logo2.svg',
    nav: [
      { text: '指南', link: '/' },
      { text: '参考', link: '/markdown-examples' },
      {
        text: '2.0.0-pragmatic-ddd',
        items: [
          { text: '2.0.0', link: '/item-1' },
          { text: '更新日志', link: '/item-2' },
          { text: '参与贡献', link: '/item-3' }
        ]
      }
    ],

    sidebar: [
      {
        text: 'Examples',
        items: [
          { text: 'Markdown Examples', link: '/markdown-examples' },
          { text: 'Runtime API Examples', link: '/api-examples' }
        ]
      }
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/vuejs/vitepress' }
    ]
  }
})
