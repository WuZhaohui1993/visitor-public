<script setup>
import logoSrc from './assets/logosy.png'
</script>

<template>
  <div class="app-shell">
    <header class="app-brand">
      <img :src="logoSrc" alt="系统标识" class="brand-logo" />
    </header>
    <router-view />
  </div>
</template>
