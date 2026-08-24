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
            { text: '设计理念', link: '/getting-started/design-philosophy' },
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
            { text: '领域服务', link: '/core/domain-service' },
            { text: '领域事件', link: '/core/domain-events' },
            { text: '领域操作', link: '/core/domain-operation' },
            { text: '业务规则引擎', link: '/core/business-rules' },
            { text: '应用服务', link: '/core/application-service' },
            { text: '仓储写模型', link: '/core/repository-write' },
            { text: '投影读模型', link: '/core/projection-read' },
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
            { text: 'RocketMQ 集成', link: '/integration/rocketmq' },
            { text: '事务性发件箱（Outbox）', link: '/integration/outbox' }
          ]
        }
      ],
      '/best-practices/': [
        {
          text: '最佳实践',
          items: [
            { text: '模式库总览', link: '/best-practices/' },
            { text: '聚合设计原则', link: '/best-practices/aggregate-design' },
            { text: '聚合数据库设计原则', link: '/best-practices/aggregate-database-design' },
            { text: '普通实体设计', link: '/best-practices/entity-design' },
            { text: '应用服务层协作', link: '/best-practices/application-collaboration' },
            { text: '注册表设计', link: '/best-practices/registry-design' },
            { text: '操作注册表设计', link: '/best-practices/operation-registry-design' },
            { text: '值对象', link: '/best-practices/value-object' },
            { text: '枚举值对象', link: '/best-practices/enum-value' },
            { text: '聚合业务规则（OrderRule 范式）', link: '/best-practices/order-rule-pattern' },
            { text: '校验规则领域服务', link: '/best-practices/rule-validation' },
            { text: '仓储设计原则', link: '/best-practices/repository-design' },
            { text: '投影读模型设计原则', link: '/best-practices/projection-design' },
            { text: 'MySQL 配置设计原则', link: '/best-practices/mysql-config' },
            { text: 'Elasticsearch 配置设计原则', link: '/best-practices/elasticsearch-config' },
            { text: '事件建模指南', link: '/best-practices/event-modeling' },
            { text: '事务性发件箱', link: '/best-practices/transactional-outbox' },
            { text: 'RocketMQ 配置设计原则', link: '/best-practices/rocketmq-config' },
            { text: 'Outbox 链路装配', link: '/best-practices/outbox-config' }
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
      { icon: 'github', link: 'https://github.com/pragmatic-lee/pragmatic-ddd.git' },
      {
        icon:{svg:'<svg width="72px" height="72px" viewBox="0 0 72 72" version="1.1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">\n    <title>logo_gitee_g_red@1x</title>\n    <g id="LOGO" stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">\n        <g id="Artboard-7" transform="translate(-192.000000, -115.000000)" fill="#C71D23">\n            <path d="M228,115 C247.882251,115 264,131.117749 264,151 C264,170.882251 247.882251,187 228,187 C208.117749,187 192,170.882251 192,151 C192,131.117749 208.117749,115 228,115 Z M246.223335,131 C246.222967,131 246.2226,131 246.222232,131.001102 L221.33326,131.001102 C213.969504,131.001102 208,136.970606 208,144.334362 L208,169.222232 C208,170.204066 208.795934,171 209.777768,171 L236.000329,171 C242.627564,171 248,165.627564 248,159.000329 L248,148.778425 C248,147.796591 247.204066,147.000657 246.222232,147.000657 L225.7779,147.000657 C224.796248,147.001123 224.000389,147.796773 223.999667,148.778425 L223.998503,153.222667 C223.997802,154.155409 224.715909,154.920565 225.629522,154.994969 L225.775805,155.00042 L225.775805,155.00042 L238.222276,155.000317 C239.155019,155.000295 239.919992,155.718618 239.994164,156.63225 L240.000044,156.77807 L240.000044,156.77807 L240.000044,157.666909 C240.000044,160.612411 237.612243,163.000213 234.66674,163.000213 L217.776621,163.000213 C216.794928,163.000164 215.999101,162.204358 215.999025,161.222665 L215.998559,144.334184 C215.998337,141.462319 218.268172,139.120556 221.111731,139.005187 L221.331716,139.00088 L221.331716,139.00088 L246.21727,139.00088 C247.198674,138.999777 247.994429,138.204515 247.996141,137.223112 L247.998897,132.77887 C248.000609,131.797037 247.205169,131.000609 246.223335,131 Z" id="logo_gitee_g_red"></path>\n        </g>\n    </g>\n</svg>'},
        link:'https://gitee.com/wizard-lee/pragmatic-ddd.git'
      }
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
