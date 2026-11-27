import { useMutation } from '@tanstack/react-query'
import { App, Form, Input, Modal, Space, Typography } from 'antd'
import { Link } from 'react-router'
import { governanceApi } from '@/backoffice/api/governance'
import type { Approval } from '@/backoffice/api/types'
import { errorMessage } from '@/backoffice/platform'
import { label, toolLabel } from '@/backoffice/utils/labels'
import { ArgumentsView } from './ArgumentsView'
import { boPath } from '@/backoffice/routes'

interface Props {
  readonly open: boolean
  readonly toolName: string
  readonly args: Record<string, unknown>
  readonly current?: Record<string, unknown>
  readonly title?: string
  readonly onClose: () => void
  readonly onRequested?: (approval: Approval) => void
}

export function RequestApprovalModal({ open, toolName, args, current, title, onClose, onRequested }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<{ reason: string }>()

  const mutation = useMutation({
    mutationFn: (reason: string) => governanceApi.requestApproval({ toolName, arguments: args, reason }),
    onSuccess: (approval) => {
      message.success(
        <span>
          승인 요청 #{approval.id}를 올렸습니다. <Link to={boPath(`/approvals/${approval.id}`)}>승인함에서 보기</Link>
        </span>,
      )
      form.resetFields()
      onRequested?.(approval)
      onClose()
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Modal
      open={open}
      title={title ?? `${label(toolLabel, toolName)} 승인 요청`}
      okText="승인 요청"
      cancelText="닫기"
      confirmLoading={mutation.isPending}
      onCancel={onClose}
      onOk={() => form.validateFields().then((v) => mutation.mutate(v.reason))}
      destroyOnHidden
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <Typography.Text type="secondary">
          승인자가 승인하면 그때 실행합니다. 본인 요청은 본인이 승인할 수 없습니다.
        </Typography.Text>
        <ArgumentsView args={args} current={current} />
        <Form form={form} layout="vertical">
          <Form.Item name="reason" label="요청 사유" rules={[{ required: true, message: '사유를 적어 주세요.' }]}>
            <Input.TextArea rows={3} maxLength={500} placeholder="왜 이 변경이 필요한지 적습니다" />
          </Form.Item>
        </Form>
      </Space>
    </Modal>
  )
}
