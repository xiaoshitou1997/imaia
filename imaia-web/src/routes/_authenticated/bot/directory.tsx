import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api } from '@/features/imaia/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
// directory
export const Route = createFileRoute('/_authenticated/bot/directory')({
  component: DirectoryPage,
})

function DirectoryPage() {
  const qc = useQueryClient();
  const { data: providers, isLoading } = useQuery({ queryKey: ['providers'], queryFn: api.providers });
  const [np, setNp] = useState({ name: '', baseUrl: '', apiKey: '' });
  const [nm, setNm] = useState<{ pid: number; modelId: string; name: string; protocol: string; inputs: string; outputs: string; maxOut: string }>({ pid: 0, modelId: '', name: '', protocol: 'openai-completions', inputs: 'text', outputs: 'text', maxOut: '' });
  const done = (msg: string) => { toast.success(msg); qc.invalidateQueries({ queryKey: ['providers'] }); };
  const fail = (e: Error) => toast.error(e.message);
  const createProvider = () => api.createProvider(np).then(() => { done('Provider 已新增'); setNp({ name: '', baseUrl: '', apiKey: '' }); }).catch(fail);
  const deleteProvider = (id: number) => { if (confirm('删除该 provider？')) api.deleteProvider(id).then(() => done('已删除')).catch(fail); };
  const toggle = (id: number, enabled: boolean) => api.updateProvider(id, { enabled }).then(() => done('已更新')).catch(fail);
  const createModel = (pid: number) => {
    if (nm.pid !== pid) { setNm({ ...nm, pid }); return; }
    api.createModel ? null : null;
    fetch('/api/providers/' + pid + '/models', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
      modelId: nm.modelId, name: nm.name, protocol: nm.protocol,
      inputModalities: nm.inputs.split(',').map((s) => s.trim()).filter(Boolean),
      outputModalities: nm.outputs.split(',').map((s) => s.trim()).filter(Boolean),
      maxOutputTokens: nm.maxOut ? Number(nm.maxOut) : null, params: {},
    }) }).then((r) => { if (!r.ok) throw new Error(String(r.status)); return r.json(); })
      .then(() => { toast.success('模型已新增'); setNm({ ...nm, pid: 0, modelId: '', name: '' }); qc.invalidateQueries({ queryKey: ['providers'] }); })
      .catch(fail);
  };
  const deleteModel = (id: number) => { if (confirm('删除该模型？')) api.deleteModel(id).then(() => done('已删除')).catch(fail); };
  if (isLoading) return <div className='text-muted-foreground'>加载中…</div>;
  return (
    <div className='space-y-4'>
      <Card>
        <CardHeader><CardTitle className='text-sm'>Providers（API key 打码显示；改动保存即生效，换 key 不用重启）</CardTitle></CardHeader>
        <CardContent className='space-y-3'>
          <Table>
            <TableHeader><TableRow><TableHead>名称</TableHead><TableHead>Base URL</TableHead><TableHead>API Key</TableHead><TableHead>启用</TableHead><TableHead>操作</TableHead></TableRow></TableHeader>
            <TableBody>
              {(providers || []).map((p) => (
                <TableRow key={p.id}>
                  <TableCell>{p.name}</TableCell>
                  <TableCell className='text-muted-foreground text-xs'>{p.baseUrl}</TableCell>
                  <TableCell className='text-muted-foreground text-xs'>{p.apiKeyMasked || '—'}</TableCell>
                  <TableCell><Switch checked={p.enabled} onCheckedChange={(v) => toggle(p.id, v)} /></TableCell>
                  <TableCell><Button size='sm' variant='destructive' onClick={() => deleteProvider(p.id)}>删除</Button></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <div className='grid grid-cols-3 gap-3'>
            <Input placeholder='名称' value={np.name} onChange={(e) => setNp({ ...np, name: e.target.value })} />
            <Input placeholder='Base URL（版本化 API 根）' value={np.baseUrl} onChange={(e) => setNp({ ...np, baseUrl: e.target.value })} />
            <Input placeholder='API Key' value={np.apiKey} onChange={(e) => setNp({ ...np, apiKey: e.target.value })} />
          </div>
          <Button onClick={createProvider}>新增 Provider</Button>
        </CardContent>
      </Card>
      {(providers || []).map((p) => (
        <Card key={p.id}>
          <CardHeader><CardTitle className='text-sm'>{p.name} 的模型</CardTitle></CardHeader>
          <CardContent className='space-y-3'>
            <Table>
              <TableHeader><TableRow><TableHead>model id</TableHead><TableHead>名称</TableHead><TableHead>protocol</TableHead><TableHead>输入 → 输出</TableHead><TableHead>max-out</TableHead><TableHead>params</TableHead><TableHead>操作</TableHead></TableRow></TableHeader>
              <TableBody>
                {p.models.map((m) => (
                  <TableRow key={m.id}>
                    <TableCell className='text-muted-foreground text-xs'>{m.modelId}</TableCell>
                    <TableCell>{m.name}</TableCell>
                    <TableCell><Badge variant='outline'>{m.protocol}</Badge></TableCell>
                    <TableCell className='text-muted-foreground text-xs'>{m.inputModalities.join(',')} → {m.outputModalities.join(',')}</TableCell>
                    <TableCell className='text-muted-foreground text-xs'>{m.maxOutputTokens || '—'}</TableCell>
                    <TableCell className='text-muted-foreground text-xs'>{JSON.stringify(m.params)}</TableCell>
                    <TableCell><Button size='sm' variant='destructive' onClick={() => deleteModel(m.id)}>删除</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <div className='grid grid-cols-3 gap-3'>
              <Input placeholder='model id' value={nm.modelId} onChange={(e) => setNm({ ...nm, pid, modelId: e.target.value })} />
              <Input placeholder='名称' value={nm.name} onChange={(e) => setNm({ ...nm, pid, name: e.target.value })} />
              <select className='border-input bg-background rounded-md border px-3 py-2 text-sm' value={nm.protocol} onChange={(e) => setNm({ ...nm, pid, protocol: e.target.value })}>
                <option>openai-completions</option><option>openai-images</option><option>openai-video</option><option>openai-audio-speech</option><option>openai-audio-transcription</option>
              </select>
              <Input placeholder='输入模态（逗号分隔）' value={nm.inputs} onChange={(e) => setNm({ ...nm, pid, inputs: e.target.value })} />
              <Input placeholder='输出模态（逗号分隔）' value={nm.outputs} onChange={(e) => setNm({ ...nm, pid, outputs: e.target.value })} />
              <Input placeholder='max output tokens' value={nm.maxOut} onChange={(e) => setNm({ ...nm, pid, maxOut: e.target.value })} />
            </div>
            <Button size='sm' onClick={() => createModel(p.id)}>为此 Provider 新增模型</Button>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
