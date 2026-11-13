import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, type Connection } from '@/features/imaia/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'

export const Route = createFileRoute('/_authenticated/bot/connections')({
  component: ConnectionsPage,
})

const fmt = (t: string | null | undefined) => (t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '—')

function ConnectionsPage() {
  const qc = useQueryClient()
  const connections = useQuery({ queryKey: ['connections'], queryFn: api.connections })

  const invalidate = () => qc.invalidateQueries({ queryKey: ['connections'] })
  const fail = (e: Error) => toast.error(e.message)

  const toggle = (c: Connection, on: boolean) =>
    api.updateConnection(c.id, { enabled: on })
      .then(() => { toast.success(on ? '已启用，重启后生效' : '已停用，重启后该平台不再启动'); invalidate() })
      .catch(fail)

  const remove = (c: Connection) => {
    if (!confirm('删除连接「' + c.name + '」？已有会话数据的连接会被拒绝。')) return
    api.deleteConnection(c.id).then(() => { toast.success('已删除'); invalidate() }).catch(fail)
  }

  return (
    <div className='space-y-4'>
      <NewConnectionForm onDone={invalidate} />
      <Card>
        <CardHeader className='pb-2'>
          <CardTitle className='text-sm'>
            接入连接（凭据只显示掩码，原样提交不会覆盖真实值；改动重启生效）
          </CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>平台</TableHead>
                <TableHead>名称</TableHead>
                <TableHead>配置（掩码）</TableHead>
                <TableHead>更新时间</TableHead>
                <TableHead>启用</TableHead>
                <TableHead>操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(connections.data || []).map((c) => (
                <TableRow key={c.id}>
                  <TableCell><Badge variant='outline'>{c.platform}</Badge></TableCell>
                  <TableCell className='text-sm'>{c.name}</TableCell>
                  <TableCell className='text-muted-foreground max-w-md truncate font-mono text-xs'>
                    {c.config}
                  </TableCell>
                  <TableCell className='text-muted-foreground text-xs'>{fmt(c.updatedAt)}</TableCell>
                  <TableCell>
                    <Switch checked={c.enabled} onCheckedChange={(on) => toggle(c, on)} />
                  </TableCell>
                  <TableCell>
                    <EditConnectionButton connection={c} onDone={invalidate} />
                    <Button size='sm' variant='destructive' className='ml-1' onClick={() => remove(c)}>删除</Button>
                  </TableCell>
                </TableRow>
              ))}
              {(connections.data || []).length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className='text-muted-foreground text-center text-xs'>
                    暂无连接；数据库迁移已内置微信/飞书种子连接
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}

function NewConnectionForm({ onDone }: { onDone: () => void }) {
  const [platform, setPlatform] = useState('wechat')
  const [name, setName] = useState('')
  const [configText, setConfigText] = useState('{}')

  const create = () => {
    let config: unknown
    try {
      config = JSON.parse(configText || '{}')
    } catch {
      toast.error('config 不是合法 JSON')
      return
    }
    api.createConnection({ platform, name, enabled: false, config })
      .then(() => { toast.success('连接已创建（默认停用；填好凭据后再启用）'); setName(''); setConfigText('{}'); onDone() })
      .catch((e: Error) => toast.error(e.message))
  }

  return (
    <Card>
      <CardHeader className='pb-2'>
        <CardTitle className='text-sm'>新连接</CardTitle>
      </CardHeader>
      <CardContent className='space-y-3'>
        <div className='flex flex-wrap items-center gap-3'>
          <select
            className='border-input bg-background rounded-md border px-3 py-2 text-sm'
            value={platform}
            onChange={(e) => setPlatform(e.target.value)}
          >
            <option value='wechat'>wechat（个人号 daemon）</option>
            <option value='feishu'>feishu（自建应用）</option>
          </select>
          <Input placeholder='名称（如 default）' className='w-48' value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <Textarea
          className='font-mono text-xs'
          rows={4}
          placeholder={'结构化配置 JSON，如 {"baseUrl": "http://127.0.0.1:6174", "token": "..."}'}
          value={configText}
          onChange={(e) => setConfigText(e.target.value)}
        />
        <Button onClick={create}>创建</Button>
      </CardContent>
    </Card>
  )
}

function EditConnectionButton({ connection, onDone }: { connection: Connection; onDone: () => void }) {
  const [open, setOpen] = useState(false)
  const [name, setName] = useState(connection.name)
  const [configText, setConfigText] = useState(connection.config)

  const save = () => {
    let config: unknown
    try {
      config = JSON.parse(configText || '{}')
    } catch {
      toast.error('config 不是合法 JSON')
      return
    }
    api.updateConnection(connection.id, { name, config })
      .then(() => {
        toast.success('已保存（重启生效）；提交的掩码值已自动还原为真实凭据')
        setOpen(false)
        onDone()
      })
      .catch((e: Error) => toast.error(e.message))
  }

  if (!open) {
    return <Button size='sm' variant='ghost' onClick={() => { setName(connection.name); setConfigText(connection.config); setOpen(true) }}>编辑</Button>
  }
  return (
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/60' onClick={() => setOpen(false)}>
      <div className='bg-background w-full max-w-lg space-y-3 rounded-lg border p-4' onClick={(e) => e.stopPropagation()}>
        <div className='text-sm font-medium'>编辑连接 {connection.platform} / {connection.name}</div>
        <Input value={name} onChange={(e) => setName(e.target.value)} />
        <Textarea
          className='font-mono text-xs'
          rows={8}
          value={configText}
          onChange={(e) => setConfigText(e.target.value)}
        />
        <div className='text-muted-foreground text-xs'>
          掩码 •••••••• 提交时自动还原为存储的真实凭据，无需改动这些字段
        </div>
        <div className='flex justify-end gap-2'>
          <Button size='sm' variant='ghost' onClick={() => setOpen(false)}>取消</Button>
          <Button size='sm' onClick={save}>保存</Button>
        </div>
      </div>
    </div>
  )
}
