<script setup lang="ts">
// 工信部备案号（ICP）
const icpNo = '京ICP备2026056027号'
const icpLink = 'https://beian.miit.gov.cn/'
</script>

<template>
  <div class="beian-footer">
    <a :href="icpLink" target="_blank" rel="noopener noreferrer">{{ icpNo }}</a>
  </div>
</template>

<style scoped>
.beian-footer {
  max-width: var(--vp-layout-max-width, 1440px);
  margin: 0 auto;
  padding: 12px 32px;
  text-align: center;
  font-size: 13px;
  color: var(--vp-c-text-2);
  border-top: 1px solid var(--vp-c-divider);
}

.beian-footer a {
  color: var(--vp-c-text-2);
  text-decoration: none;
}

.beian-footer a:hover {
  color: var(--vp-c-brand-1);
  text-decoration: underline;
}
</style>
