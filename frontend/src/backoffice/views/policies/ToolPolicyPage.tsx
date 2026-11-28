import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Form, InputNumber, Modal, Select, Space, Switch, Table, Tag, Typography } from 'antd'
import { useState } from 'react'
import { governanceApi } from '@/backoffice/api/governance'
import type { RiskLevel, ToolPolicy, ToolPolicyCommand } from '@/backoffice/api/types'
import { StatusTag } from '@/backoffice/components/StatusTag'
import { errorMessage, usePermission } from '@/backoffice/platform'
import { label, riskColor, riskLabel, toolLabel } from '@/backoffice/utils/labels'
import { fmtDateTime } from '@/backoffice/utils/time'

const PERMISSIONS = [
  'content:read', 'content:write', 'schedule:read', 'schedule:draft', 'schedule:publish',
  'issue:read', 'issue:write', 'exposure:write', 'approval:decide', 'audit:read', 'policy:write',
]
const RISKS: RiskLevel[] = ['READ', 'WRITE_LOW', 'WRITE_HIGH']

function EditModal({ policy, onClose }: { readonly policy: ToolPolicy; readonly onClose: () => void }) {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<ToolPolicyCommand>()
  const risk = Form.useWatch('riskLevel', form)
  const save = useMutation({
    mutationFn: (command: ToolPolicyCommand) => governanceApi.updateToolPolicy(policy.toolName, command),
    onSuccess: () => {
      message.success('툴 정책을 바꿨습니다. mcp-server는 잠시 뒤 새 정책을 읽습니다.')
      void queryClient.invalidateQueries({ queryKey: ['tool-policies'] })
      onClose()
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Modal
      open
      title={`툴 정책 · ${policy.toolName}`}
      okText="저장"
      cancelText="닫기"
      confirmLoading={save.isPending}
      onCancel={onClose}
      onOk={() => form.validateFields().then((v) => save.mutate(v))}
      destroyOnHidden
    >
      <Form<ToolPolicyCommand>
        form={form}
        layout="vertical"
        initialValues={{
          riskLevel: policy.riskLevel,
          requiredPermission: policy.requiredPermission,
          enabled: policy.enabled,
          approvalTtlMinutes: policy.approvalTtlMinutes,
        }}
      >
        <Form.Item name="enabled" label="사용" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="riskLevel" label="위험 등급" extra="변경(승인)으로 두면 실행하지 않고 승인 요청을 올립니다.">
          <Select options={RISKS.map((r) => ({ value: r, label: riskLabel[r] }))} />
        </Form.Item>
        <Form.Item name="requiredPermission" label="필요 권한">
          <Select options={PERMISSIONS.map((p) => ({ value: p, label: p }))} />
        </Form.Item>
        <Form.Item
          name="approvalTtlMinutes"
          label="승인 유효 시간 분"
          rules={[{ required: true }]}
          extra={risk === 'WRITE_HIGH' ? '이 시간 안에 결정하지 않으면 만료됩니다.' : '승인이 필요 없는 등급이라 쓰지 않습니다.'}
        >
          <InputNumber min={0} max={10080} style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  )
}

export function ToolPolicyPage() {
  const { can } = usePermission()
  const canWrite = can('policy:write')
  const [editing, setEditing] = useState<ToolPolicy | null>(null)
  const policies = useQuery({ queryKey: ['tool-policies'], queryFn: governanceApi.toolPolicies })

  return (
    <Card title="툴 정책">
      <Space direction="vertical" style={{ width: '100%' }}>
        <Typography.Text type="secondary">
          에이전트와 MCP가 쓰는 툴마다 위험 등급, 필요 권한, 승인 유효 시간을 정합니다. 승인과 반려는 툴로 주지 않습니다.
        </Typography.Text>
        {policies.error && <Alert type="error" message={errorMessage(policies.error)} />}
        <Table<ToolPolicy>
          rowKey="toolName"
          size="small"
          loading={policies.isLoading}
          dataSource={policies.data ?? []}
          pagination={false}
          columns={[
            { title: '영역', dataIndex: 'domain', width: 100, filters: [...new Set((policies.data ?? []).map((p) => p.domain))].map((d) => ({ text: d, value: d })), onFilter: (v, r) => r.domain === v },
            {
              title: '툴',
              dataIndex: 'toolName',
              render: (v: string) => (
                <Space direction="vertical" size={0}>
                  {label(toolLabel, v)}
                  <Typography.Text type="secondary" code>
                    {v}
                  </Typography.Text>
                </Space>
              ),
            },
            { title: '위험', dataIndex: 'riskLevel', width: 110, render: (v: string) => <StatusTag value={v} labels={riskLabel} colors={riskColor} /> },
            { title: '필요 권한', dataIndex: 'requiredPermission', width: 150, render: (v: string) => <code>{v}</code> },
            { title: '승인 유효', dataIndex: 'approvalTtlMinutes', width: 100, render: (v: number, r) => (r.riskLevel === 'WRITE_HIGH' ? `${v}분` : '-') },
            { title: '사용', dataIndex: 'enabled', width: 80, render: (v: boolean) => (v ? <Tag color="green">켜짐</Tag> : <Tag>꺼짐</Tag>) },
            { title: '바뀐 일시', dataIndex: 'updatedAt', width: 150, render: (v: string) => fmtDateTime(v) },
            ...(canWrite
              ? [{ title: '', key: 'edit', width: 70, render: (_: unknown, r: ToolPolicy) => <Button size="small" onClick={() => setEditing(r)}>고치기</Button> }]
              : []),
          ]}
        />
      </Space>
      {editing && <EditModal policy={editing} onClose={() => setEditing(null)} />}
    </Card>
  )
}
