import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 权限指令：v-perm="'sys:user:create'"
 * 无对应权限码时直接移除元素；传数组时拥有其中任一权限即可。
 */
export const perm: Directive<HTMLElement, string | string[]> = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    const store = useUserStore()
    const required = binding.value
    if (!required || (Array.isArray(required) && required.length === 0)) {
      return
    }
    const codes = Array.isArray(required) ? required : [required]
    const allowed = codes.some((code) => store.hasPerm(code))
    if (!allowed) {
      el.parentNode?.removeChild(el)
    }
  },
}

export default perm
