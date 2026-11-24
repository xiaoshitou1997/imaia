import { FormItem, FormLabel } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'

/** 数字以字符串进出表单，提交时统一转换，避免中途格式化打断输入。 */
export function NumberInput({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (v: string) => void
}) {
  return (
    <FormItem>
      <FormLabel>{label}</FormLabel>
      <Input
        inputMode='numeric'
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </FormItem>
  )
}

export function ToggleInput({
  label,
  checked,
  onChange,
}: {
  label: string
  checked: boolean
  onChange: (v: boolean) => void
}) {
  return (
    <div className='flex items-center justify-between rounded-md border p-3'>
      <FormLabel className='font-normal'>{label}</FormLabel>
      <Switch checked={checked} onCheckedChange={onChange} />
    </div>
  )
}
