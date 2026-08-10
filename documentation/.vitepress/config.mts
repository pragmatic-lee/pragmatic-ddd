import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: "Pragmatic DDD",
  description: "Pragmatic DDD 是一个以\"务实\"为设计哲学的领域驱动设计（DDD）框架",
  ignoreDeadLinks: true,
  lastUpdated: true,
  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    logo: '/logo2.svg',
    nav: [
      { text: '指南', link: '/getting-started/overview' },
      { text: '核心', link: '/core/domain-modeling' },
      { text: '集成', link: '/integration/mybatis' },
      { text: '最佳实践', link: '/best-practices/aggregate-design' },
      { text: '参考', link: '/reference/api-index' },
      {
        text: '2.0.0',
        items: [
          { text: '更新日志', link: 'https://github.com/lixiaojing/pragmatic-ddd/blob/main/CHANGELOG.md' },
          { text: '参与贡献', link: 'https://github.com/lixiaojing/pragmatic-ddd/blob/main/CONTRIBUTING.md' }
        ]
      }
    ],

    sidebar: {
      '/getting-started/': [
        {
          text: '开始',
          items: [
            { text: '框架概览', link: '/getting-started/overview' },
            { text: '快速开始', link: '/getting-started/quick-start' },
            { text: '推荐项目结构', link: '/getting-started/project-structure' }
          ]
        }
      ],
      '/core/': [
        {
          text: '核心模块',
          items: [
            { text: '领域建模', link: '/core/domain-modeling' },
            { text: '业务规则引擎', link: '/core/business-rules' },
            { text: '领域事件', link: '/core/domain-events' },
            { text: '应用服务', link: '/core/application-service' },
            { text: '仓储', link: '/core/repository' },
            { text: '操作追踪', link: '/core/operation-tracking' },
            { text: '变更追踪', link: '/core/change-tracking' },
            { text: '防腐层（ACL）', link: '/core/acl' },
            { text: '外部依赖声明', link: '/core/dependency' },
            { text: '配置体系', link: '/core/configuration' },
            { text: '对外广播', link: '/core/broadcast' }
          ]
        }
      ],
      '/integration/': [
        {
          text: '集成模块',
          items: [
            { text: 'MyBatis 集成', link: '/integration/mybatis' },
            { text: 'RocketMQ 集成', link: '/integration/rocketmq' }
          ]
        }
      ],
      '/best-practices/': [
        {
          text: '最佳实践',
          items: [
            { text: '聚合设计原则', link: '/best-practices/aggregate-design' },
            { text: '事件建模指南', link: '/best-practices/event-modeling' },
            { text: '事务性发件箱', link: '/best-practices/transactional-outbox' }
          ]
        }
      ],
      '/reference/': [
        {
          text: '参考',
          items: [
            { text: 'API 速查索引', link: '/reference/api-index' },
            { text: '配置项参考', link: '/reference/configuration' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/lixiaojing/pragmatic-ddd' }
    ],

    outline: {
      level: [2, 3]
    },

    docFooter: {
      prev: '上一页',
      next: '下一页'
    }
  }
})
