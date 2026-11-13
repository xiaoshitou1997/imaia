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
  const [modelsDevId, setModelsDevId] = useState('')

  const create = () => {
    api.createProvider({ name, baseUrl, apiKey: apiKey.trim() || undefined, modelsDevId: modelsDevId.trim() || undefined })
      .then((res) => {
        toast.success('接入商已创建：' + res.sync)
        setName(''); setBaseUrl(''); setApiKey(''); setModelsDevId('')
        onDone()
      })
      .catch((e: Error) => toast.error(e.message))
  }

  return (
    <Card>
      <CardHeader className='pb-2'>
        <CardTitle className='text-sm'>
          新接入商（OpenAI 协议族；网页搜索走名为 tavily 的接入商；模型清单创建后自动从 /v1/models 同步）
        </CardTitle>
      </CardHeader>
      <CardContent className='flex flex-wrap items-center gap-3'>
        <Input placeholder='名称（如 zhipu）' className='w-40' value={name} onChange={(e) => setName(e.target.value)} />
        <Input placeholder='Base URL（…/v1）' className='w-80' value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} />
        <Input placeholder='API Key（可选）' className='w-60' value={apiKey} onChange={(e) => setApiKey(e.target.value)} />
        <Input placeholder='models.dev ID（可选，富化元数据）' className='w-56' value={modelsDevId} onChange={(e) => setModelsDevId(e.target.value)} />
        <Button onClick={create}>创建</Button>
      </CardContent>
    </Card>
  )
}

const fail = (e: Error) => toast.error(e.message)

function ProviderCard({ provider, onDone }: { provider: Provider; onDone: () => void }) {
  const [baseUrl, setBaseUrl] = useState(provider.baseUrl)
  const [apiKey, setApiKey] = useState(provider.apiKey)
  const [modelsDevId, setModelsDevId] = useState(provider.modelsDevId ?? '')
  const [form, setForm] = useState({ modelId: '', name: '', modalitiesIn: 'text', modalitiesOut: 'text' })

  const toggle = (on: boolean) =>
    api.updateProvider(provider.id, { enabled: on }).then(onDone).catch(fail)

  const saveBase = () =>
    api.updateProvider(provider.id, {
      baseUrl,
      apiKey: apiKey === provider.apiKey ? undefined : apiKey,
      modelsDevId: modelsDevId.trim() || null,
    }).then(() => { toast.success('连接已保存'); onDone() }).catch(fail)

  const sync = () =>
    api.syncModels(provider.id).then((res) => {
      toast.success(`同步完成：目录 ${res.listed}，新增 ${res.added}，更新 ${res.updated}，下架 ${res.retired}`)
      onDone()
    }).catch(fail)

  const remove = () => {
    if (!confirm('删除接入商会一并删除其模型目录，确认？')) return
    api.deleteProvider(provider.id).then(() => { toast.success('已删除'); onDone() }).catch(fail)
  }

  const addModel = () => {
    if (!form.modelId.trim()) {
      toast.error('modelId 必填')
      return
    }
    api.createModel(provider.id, {
      modelId: form.modelId.trim(),
      name: form.name.trim() || undefined,
      modalitiesIn: form.modalitiesIn,
      modalitiesOut: form.modalitiesOut,
    }).then(() => { toast.success('模型已登记'); setForm({ modelId: '', name: '', modalitiesIn: 'text', modalitiesOut: 'text' }); onDone() })
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
        <div className='flex items-center gap-2'>
          <Button size='sm' variant='secondary' onClick={sync}>同步模型</Button>
          <Switch checked={provider.enabled} onCheckedChange={toggle} />
        </div>
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
          <Input
            className='w-56'
            placeholder='models.dev ID'
            value={modelsDevId}
            onChange={(e) => setModelsDevId(e.target.value)}
          />
          <Button size='sm' variant='secondary' onClick={saveBase}>保存连接</Button>
          <Button size='sm' variant='destructive' onClick={remove}>删除接入商</Button>
        </div>

        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Model ID</TableHead>
              <TableHead>名称</TableHead>
              <TableHead>模态（入 → 出）</TableHead>
              <TableHead>能力</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>启用</TableHead>
              <TableHead>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {provider.models.map((model) => (
              <TableRow key={model.id}>
                <TableCell className='text-xs font-medium'>{model.modelId}</TableCell>
                <TableCell className='text-xs'>{model.name ?? '—'}</TableCell>
                <TableCell className='text-muted-foreground text-xs'>
                  {model.modalitiesIn} → {model.modalitiesOut}
                </TableCell>
                <TableCell className='space-x-1'>
                  {model.vision && <Badge variant='outline'>vision</Badge>}
                  {model.toolCall && <Badge variant='outline'>tools</Badge>}
                  {model.reasoning && <Badge variant='outline'>reasoning</Badge>}
                  {!model.vision && !model.toolCall && !model.reasoning && (
                    <span className='text-muted-foreground text-xs'>—</span>
                  )}
                </TableCell>
                <TableCell className='space-x-1'>
                  <Badge variant={model.available ? 'outline' : 'secondary'}>
                    {model.available ? '网关可用' : '网关已消失'}
                  </Badge>
                  <Badge variant='outline'>{model.source === 'manual' ? '手动登记' : '同步'}</Badge>
                </TableCell>
                <TableCell>
                  <Switch
                    checked={model.enabled}
                    onCheckedChange={(on) => api.updateModel(model.id, { enabled: on }).then(onDone).catch(fail)}
                  />
                </TableCell>
                <TableCell>
                  <Button
                    size='sm'
                    variant='destructive'
                    onClick={() => api.deleteModel(model.id).then(() => { toast.success('已删除'); onDone() }).catch(fail)}
                  >删除</Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>

        <div className='flex flex-wrap items-center gap-3'>
          <Input placeholder='model-id（必填）' className='w-44' value={form.modelId} onChange={(e) => setForm({ ...form, modelId: e.target.value })} />
          <Input placeholder='显示名（可选）' className='w-36' value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <Input placeholder='输入模态（如 text,image）' className='w-52' value={form.modalitiesIn} onChange={(e) => setForm({ ...form, modalitiesIn: e.target.value })} />
          <Input placeholder='输出模态（如 text）' className='w-52' value={form.modalitiesOut} onChange={(e) => setForm({ ...form, modalitiesOut: e.target.value })} />
          <Button size='sm' onClick={addModel}>手动登记模型</Button>
        </div>
      </CardContent>
    </Card>
  )
}
