import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, type Provider } from '@/features/imaia/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'

export const Route = createFileRoute('/_authenticated/bot/models')({
  component: ModelsPage,
})

const PROTOCOLS = ['openai-completions', 'openai-images', 'openai-video', 'openai-audio-speech', 'openai-audio-transcription']

function ModelsPage() {
  const qc = useQueryClient()
  const providers = useQuery({ queryKey: ['providers'], queryFn: api.providers })

  const invalidate = () => qc.invalidateQueries({ queryKey: ['providers'] })

  return (
    <div className='space-y-4'>
      <NewProviderForm onDone={invalidate} />
      {(providers.data || []).map((provider) => (
        <ProviderCard key={provider.id} provider={provider} onDone={invalidate} />
      ))}
    </div>
  )
}

function NewProviderForm({ onDone }: { onDone: () => void }) {
  const [name, setName] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [apiKey, setApiKey] = useState('')

  const create = () => {
    api.createProvider({ name, baseUrl, apiKey: apiKey || undefined })
      .then(() => { toast.success('接入商已创建'); setName(''); setBaseUrl(''); setApiKey(''); onDone() })
      .catch((e: Error) => toast.error(e.message))
  }

  return (
    <Card>
      <CardHeader className='pb-2'>
        <CardTitle className='text-sm'>新接入商（OpenAI 协议族；网页搜索走名为 tavily 的接入商）</CardTitle>
      </CardHeader>
      <CardContent className='flex flex-wrap items-center gap-3'>
        <Input placeholder='名称（如 zhipu）' className='w-40' value={name} onChange={(e) => setName(e.target.value)} />
        <Input placeholder='Base URL（…/v1）' className='w-80' value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} />
        <Input placeholder='API Key（可选）' className='w-60' value={apiKey} onChange={(e) => setApiKey(e.target.value)} />
        <Button onClick={create}>创建</Button>
      </CardContent>
    </Card>
  )
}

function ProviderCard({ provider, onDone }: { provider: Provider; onDone: () => void }) {
  const [baseUrl, setBaseUrl] = useState(provider.baseUrl)
  const [apiKey, setApiKey] = useState(provider.apiKey)
  const [form, setForm] = useState({ modelId: '', name: '', protocol: 'openai-completions', modalities: '' })

  const invalidate = () => onDone()
  const fail = (e: Error) => toast.error(e.message)

  const saveBase = () => {
    api.updateProvider(provider.id, {
      baseUrl,
      apiKey: apiKey && apiKey !== provider.apiKey ? apiKey : undefined,
    }).then(() => toast.success('已保存')).catch(fail)
  }

  const toggle = (on: boolean) =>
    api.updateProvider(provider.id, { enabled: on }).then(invalidate).catch(fail)

  const remove = () => {
    if (!confirm('删除接入商及其全部模型？')) return
    api.deleteProvider(provider.id).then(() => { toast.success('已删除'); invalidate() }).catch(fail)
  }

  const addModel = () => {
    if (!form.modelId || !form.protocol) {
      toast.error('modelId 与 protocol 必填')
      return
    }
    api.createModel(provider.id, {
      modelId: form.modelId,
      name: form.name || undefined,
      protocol: form.protocol,
      inputModalities: form.modalities ? form.modalities.split(',').map((s) => s.trim()).filter(Boolean) : undefined,
      params: {},
    })
      .then(() => { toast.success('模型已添加'); setForm({ ...form, modelId: '', name: '', modalities: '' }); invalidate() })
      .catch(fail)
  }

  return (
    <Card>
      <CardHeader className='flex flex-row items-center justify-between space-y-0 pb-2'>
        <CardTitle className='text-sm'>
          {provider.name}
          <Badge variant={provider.enabled ? 'default' : 'secondary'} className='ml-2'>
            {provider.enabled ? '启用' : '停用'}
          </Badge>
        </CardTitle>
        <Switch checked={provider.enabled} onCheckedChange={toggle} />
      </CardHeader>
      <CardContent className='space-y-4'>
        <div className='flex flex-wrap items-center gap-3'>
          <Input className='w-80' value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} />
          <Input
            className='w-60'
            placeholder='API Key（只显示掩码）'
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
          />
          <Button size='sm' variant='secondary' onClick={saveBase}>保存连接</Button>
          <Button size='sm' variant='destructive' onClick={remove}>删除接入商</Button>
        </div>

        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Model ID</TableHead>
              <TableHead>名称</TableHead>
              <TableHead>协议</TableHead>
              <TableHead>输入模态</TableHead>
              <TableHead>输出模态</TableHead>
              <TableHead>最大输出</TableHead>
              <TableHead>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {provider.models.map((model) => (
              <TableRow key={model.id}>
                <TableCell className='text-xs font-medium'>{model.modelId}</TableCell>
                <TableCell className='text-xs'>{model.name ?? '—'}</TableCell>
                <TableCell><Badge variant='outline'>{model.protocol}</Badge></TableCell>
                <TableCell className='text-muted-foreground text-xs'>{model.inputModalities}</TableCell>
                <TableCell className='text-muted-foreground text-xs'>{model.outputModalities}</TableCell>
                <TableCell className='text-muted-foreground text-xs'>{model.maxOutputTokens ?? '—'}</TableCell>
                <TableCell>
                  <Button
                    size='sm'
                    variant='destructive'
                    onClick={() => api.deleteModel(model.id).then(() => { toast.success('已删除'); invalidate() }).catch(fail)}
                  >删除</Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>

        <div className='flex flex-wrap items-center gap-3'>
          <Input placeholder='model-id' className='w-44' value={form.modelId} onChange={(e) => setForm({ ...form, modelId: e.target.value })} />
          <Input placeholder='显示名（可选）' className='w-36' value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <select
            className='border-input bg-background rounded-md border px-3 py-2 text-sm'
            value={form.protocol}
            onChange={(e) => setForm({ ...form, protocol: e.target.value })}
          >
            {PROTOCOLS.map((protocol) => <option key={protocol} value={protocol}>{protocol}</option>)}
          </select>
          <Input placeholder='输入模态（逗号分隔，如 text,image）' className='w-64' value={form.modalities} onChange={(e) => setForm({ ...form, modalities: e.target.value })} />
          <Button size='sm' onClick={addModel}>添加模型</Button>
        </div>
      </CardContent>
    </Card>
  )
}
