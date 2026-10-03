import { ref, watch, type Ref } from 'vue'

/** KPI 数字滚动（明日方舟风格：从当前值动画到目标值） */
export function useCountUp(target: Ref<number>, duration = 800) {
  const display = ref(0)
  watch(target, (val) => {
    const start = display.value
    const t0 = performance.now()
    function tick(t: number) {
      const p = Math.min((t - t0) / duration, 1)
      display.value = Math.round(start + (val - start) * (1 - Math.pow(1 - p, 3)))
      if (p < 1) requestAnimationFrame(tick)
    }
    requestAnimationFrame(tick)
  }, { immediate: true })
  return display
}
