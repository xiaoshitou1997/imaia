import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, type Settings } from '@/features/imaia/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'

export const Route = createFileRoute('/_authenticated/bot/settings')({
  component: SettingsPage,
})

function SettingsPage() {
  const query = useQuery({ queryKey: ['settings'], queryFn: api.getSettings })
  const [form, setForm] = useState<Settings | null>(null)

  useEffect(() => {
    if (query.data) setForm(query.data)
  }, [query.data])

  if (query.isLoading || !form) {
    return <div className='text-muted-foreground'>加载中…</div>
  }

  const set = (patch: Partial<Settings>) => setForm({ ...form, ...patch })

  const save = () => {
    api.updateSettings({
      systemPrompt: form.systemPrompt,
      historyLimit: form.historyLimit,
      requireGroupMention: form.requireGroupMention,
      quietHoursEnabled: form.quietHoursEnabled,
      quietHoursStart: form.quietHoursStart,
      quietHoursEnd: form.quietHoursEnd,
      enabled: form.enabled,
    })
      .then(() => toast.success('已保存，下一轮对话即生效'))
      .catch((e: Error) => toast.error(e.message))
  }

  return (
    <div className='max-w-3xl space-y-4'>
      <Card>
        <CardHeader className='flex flex-row items-center justify-between space-y-0 pb-2'>
          <CardTitle className='text-sm'>AI 总开关</CardTitle>
          <Switch checked={form.enabled} onCheckedChange={(on) => set({ enabled: on })} />
        </CardHeader>
        <CardContent className='text-muted-foreground text-xs'>
          全局总开关（第一道保险）；每条会话线上还有独立的自动回复开关（第二道保险）。
          关闭时所有消息只归档、永不回复。
        </CardContent>
      </Card>

      <Card>
        <CardHeader className='pb-2'>
          <CardTitle className='text-sm'>默认提示词（会话未配置人设时使用；机制说明由系统注入，不受此影响）</CardTitle>
        </CardHeader>
        <CardContent className='space-y-3'>
          <Textarea rows={5} value={form.systemPrompt} onChange={(e) => set({ systemPrompt: e.target.value })} />
          <div className='space-y-1'>
            <Label className='text-xs'>历史窗口（轮）</Label>
            <Input
              className='w-32'
              type='number'
              min={2}
              max={100}
              value={form.historyLimit}
              onChange={(e) => set({ historyLimit: Number(e.target.value) })}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className='pb-2'>
          <CardTitle className='text-sm'>护栏</CardTitle>
        </CardHeader>
        <CardContent className='space-y-4'>
          <div className='flex items-center justify-between'>
            <div>
              <div className='text-sm'>群聊 @ 门控</div>
              <div className='text-muted-foreground text-xs'>群消息必须真 @ 机器人才回复；未 @ 只归档留作上下文</div>
            </div>
            <Switch
              checked={form.requireGroupMention}
              onCheckedChange={(on) => set({ requireGroupMention: on })}
            />
          </div>
          <div className='flex items-center justify-between'>
            <div>
              <div className='text-sm'>静默时段</div>
              <div className='text-muted-foreground text-xs'>
                窗口内不自动回复（只约束被动回复；你自己设定的定时任务不受影响）
              </div>
            </div>
            <Switch
              checked={form.quietHoursEnabled}
              onCheckedChange={(on) => set({ quietHoursEnabled: on })}
            />
          </div>
          {form.quietHoursEnabled && (
            <div className='flex items-center gap-3'>
              <Input
                className='w-32'
                type='time'
                value={form.quietHoursStart}
                onChange={(e) => set({ quietHoursStart: e.target.value })}
              />
              <span className='text-muted-foreground text-sm'>至</span>
              <Input
                className='w-32'
                type='time'
                value={form.quietHoursEnd}
                onChange={(e) => set({ quietHoursEnd: e.target.value })}
              />
            </div>
          )}
        </CardContent>
      </Card>

      <Button onClick={save}>保存全部</Button>
    </div>
  )
}
