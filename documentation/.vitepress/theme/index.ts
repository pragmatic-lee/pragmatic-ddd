import { h } from 'vue'
import DefaultTheme from 'vitepress/theme'
import BeianFooter from './BeianFooter.vue'

export default {
  extends: DefaultTheme,
  Layout: () => {
    return h(DefaultTheme.Layout, null, {
      'layout-bottom': () => h(BeianFooter)
    })
  }
}
