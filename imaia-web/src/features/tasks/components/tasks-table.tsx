import { Route } from '@/routes/_authenticated/tasks/'
import { useQuery } from '@tanstack/react-query'
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type OnChangeFn,
  type PaginationState,
} from '@tanstack/react-table'
import { cn } from '@/lib/utils'
import { api, type TaskRow } from '@/lib/api'
import { t } from '@/lib/copy'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { DataTablePagination } from '@/components/data-table'
import { tasksColumns as columns } from './tasks-columns'

interface TasksTableProps {
  status?: string
  page?: number
}

export function TasksTable({ status }: TasksTableProps) {
  // 页码同样来自路由 search，翻页写回 URL；筛选变化时路由已写新 search，页码回落。
  const navigate = Route.useNavigate()
  const routePage = Route.useSearch().page ?? 1
  const pagination = { pageIndex: Math.max(0, routePage - 1), pageSize: 20 }

  const query = useQuery({
    queryKey: ['tasks', pagination.pageIndex, pagination.pageSize, status],
    queryFn: () =>
      api.tasks(pagination.pageIndex + 1, pagination.pageSize, status),
  })

  const setPagination: OnChangeFn<PaginationState> = (updater) => {
    const next = typeof updater === 'function' ? updater(pagination) : updater
    navigate({
      search: (prev) => ({ ...prev, page: next.pageIndex > 0 ? next.pageIndex + 1 : undefined }),
    })
  }

  const table = useReactTable({
    data: (query.data?.items ?? []) as TaskRow[],
    columns,
    manualPagination: true,
    pageCount: query.data?.totalPages ?? 1,
    state: { pagination },
    onPaginationChange: setPagination,
    getCoreRowModel: getCoreRowModel(),
    getRowId: (row) => String(row.id),
  })

  return (
    <div className='flex flex-1 flex-col gap-4'>
      <div className='overflow-hidden rounded-md border'>
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead
                    key={header.id}
                    colSpan={header.colSpan}
                    className={cn(header.column.columnDef.meta?.className)}
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                          header.column.columnDef.header,
                          header.getContext()
                        )}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {query.isError ? (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className='h-24 text-center text-muted-foreground'
                >
                  {t('state.loadFailed')}
                </TableCell>
              </TableRow>
            ) : query.isLoading ? (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className='h-24 text-center text-muted-foreground'
                >
                  加载中…
                </TableCell>
              </TableRow>
            ) : table.getRowModel().rows?.length ? (
              table.getRowModel().rows.map((row) => (
                <TableRow key={row.id}>
                  {row.getVisibleCells().map((cell) => (
                    <TableCell
                      key={cell.id}
                      className={cn(cell.column.columnDef.meta?.className)}
                    >
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext()
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className='h-24 text-center text-muted-foreground'
                >
                  没有任务。右上角「新建任务」创建一个。
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
      <DataTablePagination table={table} className='mt-auto' />
    </div>
  )
}
