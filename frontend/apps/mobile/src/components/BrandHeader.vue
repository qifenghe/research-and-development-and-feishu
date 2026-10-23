<template>
  <header class="mobile-brand" :class="{'mobile-brand--login':login}">
    <img :src="logo" alt="龙汇食品" width="44" height="48" />
    <div class="mobile-brand__copy">
      <component :is="login?'h1':'p'" class="mobile-brand__name">龙汇研发管理系统</component>
      <p class="mobile-brand__subtitle">从需求到配方 · 让研发有据可循</p>
    </div>
  </header>
</template>
<script setup lang="ts">
import logo from '../../../pc/src/assets/longhui-logo.jpg';
defineProps<{login?:boolean}>();
</script>
