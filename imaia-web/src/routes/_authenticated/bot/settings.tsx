import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api } from '@/features/imaia/api'
import type { AssistantProfile, RiskPolicy } from '@/features/imaia/api'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

export const Route = createFileRoute('/_authenticated/bot/settings')({
  component: SettingsPage,
})

function SettingsPage() {
  const { data: profileData } = useQuery({ queryKey: ['profile'], queryFn: api.profile })
  const { data: riskData } = useQuery({ queryKey: ['risk'], queryFn: api.risk })
  const { data: runtime } = useQuery({ queryKey: ['runtime'], queryFn: api.runtime })
  const [profile, setProfile] = useState<AssistantProfile | null>(null)
  const [risk, setRisk] = useState<RiskPolicy | null>(null)
  useEffect(() => { if (profileData) setProfile(profileData) }, [profileData])
  useEffect(() => { if (riskData) setRisk(riskData) }, [riskData])

  const saveProfile = () => {
    if (!profile) return
    api.updateProfile(profile).then(() => toast.success('已保存，下一轮对话生效')).catch((e: Error) => toast.error(e.message))
  }
  const saveRisk = () => {
    if (!risk) return
    api.updateRisk(risk).then(() => toast.success('已保存，立即生效')).catch((e: Error) => toast.error(e.message))
  }

  const pField = (k: keyof AssistantProfile, v: string | number | boolean) =>
    setProfile((p) => (p ? { ...p, [k]: v } : p))
  const rField = (k: keyof RiskPolicy, v: string | number) =>
    setRisk((r) => (r ? { ...r, [k]: v } : r))

  if (!profile || !risk) return <div className='text-muted-foreground'>加载中…</div>

  return (
    <Tabs defaultValue='profile' className='space-y-4'>
      <TabsList>
        <TabsTrigger value='profile'>AI 行为</TabsTrigger>
        <TabsTrigger value='risk'>风控</TabsTrigger>
        <TabsTrigger value='runtime'>daemon / stream</TabsTrigger>
      </TabsList>

      <TabsContent value='profile'>
        <Card>
          <CardContent className='space-y-3'>
            <label className='text-muted-foreground text-xs'>System Prompt</label>
            <Textarea className='min-h-32' value={profile.systemPrompt}
              onChange={(e) => pField('systemPrompt', e.target.value)} />
            <div className='grid grid-cols-2 gap-4'>
              <div>
                <label className='text-muted-foreground text-xs'>历史轮数上限</label>
                <Input type='number' value={profile.historyLimit}
                  onChange={(e) => pField('historyLimit', Number(e.target.value))} />
              </div>
              <div>
                <label className='text-muted-foreground text-xs'>回复白名单（逗号分隔 threadId）</label>
                <Input value={profile.replyThreads}
                  onChange={(e) => pField('replyThreads', e.target.value)} />
              </div>
              <div className='flex items-center gap-2'>
                <Switch checked={profile.requireGroupMention}
                  onCheckedChange={(v) => pField('requireGroupMention', v)} />
                <label className='text-muted-foreground text-xs'>群聊需 @ 才回复</label>
              </div>
              <div className='flex items-center gap-2'>
                <Switch checked={profile.ttsReply} onCheckedChange={(v) => pField('ttsReply', v)} />
                <label className='text-muted-foreground text-xs'>语音消息用语音回复</label>
              </div>
              <div>
                <label className='text-muted-foreground text-xs'>最短消息长度</label>
                <Input type='number' value={profile.minMessageLength}
                  onChange={(e) => pField('minMessageLength', Number(e.target.value))} />
              </div>
              <div className='flex items-center gap-2'>
                <Switch checked={profile.enabled} onCheckedChange={(v) => pField('enabled', v)} />
                <label className='text-muted-foreground text-xs'>AI 回复启用</label>
              </div>
              <div>
                <label className='text-muted-foreground text-xs'>回复延迟最小 ms</label>
                <Input type='number' value={profile.replyDelayMinMs}
                  onChange={(e) => pField('replyDelayMinMs', Number(e.target.value))} />
              </div>
              <div>
                <label className='text-muted-foreground text-xs'>回复延迟最大 ms</label>
                <Input type='number' value={profile.replyDelayMaxMs}
                  onChange={(e) => pField('replyDelayMaxMs', Number(e.target.value))} />
              </div>
            </div>
            <Button onClick={saveProfile}>保存</Button>
          </CardContent>
        </Card>
      </TabsContent>

      <TabsContent value='risk'>
        <Card>
          <CardContent className='space-y-3'>
            <div className='grid grid-cols-3 gap-4'>
              <div>
                <label className='text-muted-foreground text-xs'>单会话每分钟上限</label>
                <Input type='number' value={risk.repliesPerThreadPerMinute}
                  onChange={(e) => rField('repliesPerThreadPerMinute', Number(e.target.value))} />
              </div>
              <div>
                <label className='text-muted-foreground text-xs'>每小时上限</label>
                <Input type='number' value={risk.maxRepliesPerHour}
                  onChange={(e) => rField('maxRepliesPerHour', Number(e.target.value))} />
              </div>
              <div>
                <label className='text-muted-foreground text-xs'>每日上限</label>
                <Input type='number' value={risk.maxRepliesPerDay}
                  onChange={(e) => rField('maxRepliesPerDay', Number(e.target.value))} />
              </div>
              <div>
                <label className='text-muted-foreground text-xs'>静默开始（HH:mm）</label>
                <Input value={risk.quietHoursStart}
                  onChange={(e) => rField('quietHoursStart', e.target.value)} />
              </div>
              <div>
                <label className='text-muted-foreground text-xs'>静默结束（HH:mm）</label>
                <Input value={risk.quietHoursEnd}
                  onChange={(e) => rField('quietHoursEnd', e.target.value)} />
              </div>
            </div>
            <Button onClick={saveRisk}>保存</Button>
          </CardContent>
        </Card>
      </TabsContent>

      <TabsContent value='runtime'>
        <Card>
          <CardContent>
            <div className='text-muted-foreground whitespace-pre-wrap text-sm'>
              {runtime
                ? runtime.daemonBaseUrl + '\n账号: ' + runtime.daemonAccount + '\nToken: ' + runtime.daemonTokenMasked + '\n' + runtime.note
                : '加载中…'}
            </div>
          </CardContent>
        </Card>
      </TabsContent>
    </Tabs>
  )
}
