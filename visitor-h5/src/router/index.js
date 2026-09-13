import { createRouter, createWebHistory } from 'vue-router'
import RegisterPage from '../views/RegisterPage.vue'
import ResultPage from '../views/ResultPage.vue'
import ApprovePage from '../views/ApprovePage.vue'
import QueryPage from '../views/QueryPage.vue'
import CargoRegisterPage from '../views/CargoRegisterPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/visitor/register' },
    { path: '/visitor/register', component: RegisterPage },
    { path: '/visitor/query', component: QueryPage },
    { path: '/visitor/result/:bizId', component: ResultPage },
    { path: '/visitor/approve', component: ApprovePage },
    { path: '/cargo/register', component: CargoRegisterPage },
  ],
})

export default router
