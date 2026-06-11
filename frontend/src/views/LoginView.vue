<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { currentUser, login } from "../api/auth";

const router = useRouter();
const form = reactive({
  username: "admin",
  password: "1233321"
});

const notice = ref("");
const noticeType = ref("info");
const loading = ref(false);

function showNotice(type, message) {
  noticeType.value = type;
  notice.value = message;
}

async function onLogin() {
  loading.value = true;
  try {
    const { response, payload } = await login({
      username: form.username.trim(),
      password: form.password
    });
    if (response.ok) {
      const meResult = await currentUser();
      if (meResult.response.ok) {
        showNotice("success", "登录成功，正在进入业务首页...");
        setTimeout(() => {
          router.push("/workbench");
        }, 400);
      } else {
        showNotice("error", "登录态校验失败，请重试");
      }
    } else {
      const message = typeof payload === "object" && payload?.message ? payload.message : "登录失败";
      showNotice("error", `登录失败：${message}`);
    }
  } catch (error) {
    showNotice("error", `登录异常：${error.message}`);
  } finally {
    loading.value = false;
  }
}

</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <h1>登录</h1>
      <p class="sub-text">登录后可访问需要认证的业务接口。</p>
      <p class="notice" :data-type="noticeType" v-if="notice">{{ notice }}</p>

      <div class="field">
        <label>账号</label>
        <input v-model="form.username" autocomplete="username" />
      </div>
      <div class="field">
        <label>密码</label>
        <input v-model="form.password" type="password" autocomplete="current-password" />
      </div>

      <button class="primary" :disabled="loading" @click="onLogin">登录</button>

      <router-link class="switch-link" to="/register">没有账号？去注册</router-link>
    </section>
  </main>
</template>
