import { useMutation } from '@tanstack/react-query'
import { App, Form, Input, InputNumber, Modal, Select } from 'antd'
import { operationApi } from '@/backoffice/api/operation'
import type { Issue, IssueCreateCommand } from '@/backoffice/api/types'
import { errorMessage } from '@/backoffice/platform'
import { issueTypeLabel, severityLabel } from '@/backoffice/utils/labels'

interface Props {
  readonly contentId?: number
  readonly onClose: () => void
  readonly onCreated: (issue: Issue) => void
}

const options = (map: Record<string, string>) => Object.entries(map).map(([value, label]) => ({ value, label }))

export function IssueCreateModal({ contentId, onClose, onCreated }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<IssueCreateCommand>()

  const mutation = useMutation({
    mutationFn: (command: IssueCreateCommand) => operationApi.createIssue({ ...command, contentId: command.contentId ?? null }),
    onSuccess: (issue) => {
      message.success(`이슈 #${issue.id}를 등록했습니다.`)
      onCreated(issue)
      onClose()
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Modal
      open
      title="이슈 등록"
      okText="등록"
      cancelText="닫기"
      confirmLoading={mutation.isPending}
      onCancel={onClose}
      onOk={() => form.validateFields().then((v) => mutation.mutate(v))}
      destroyOnHidden
    >
      <Form<IssueCreateCommand> form={form} layout="vertical" initialValues={{ type: 'PLAYBACK_ERROR', severity: 'MEDIUM', contentId }}>
        <Form.Item name="type" label="유형" rules={[{ required: true }]}>
          <Select options={options(issueTypeLabel)} />
        </Form.Item>
        <Form.Item name="severity" label="심각도" rules={[{ required: true }]}>
          <Select options={options(severityLabel)} />
        </Form.Item>
        <Form.Item name="contentId" label="작품 번호">
          <InputNumber min={1} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="title" label="제목" rules={[{ required: true, message: '제목을 적어 주세요.' }]}>
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="description" label="내용" rules={[{ required: true, message: '내용을 적어 주세요.' }]}>
          <Input.TextArea rows={5} maxLength={4000} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
