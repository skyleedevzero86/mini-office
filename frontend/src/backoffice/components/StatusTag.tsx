import { Tag } from 'antd'
import { label } from '@/backoffice/utils/labels'

interface Props {
  readonly value: string | null | undefined
  readonly labels: Record<string, string>
  readonly colors?: Record<string, string>
}

export function StatusTag({ value, labels, colors }: Props) {
  if (!value) return <span>-</span>
  return <Tag color={colors?.[value]}>{label(labels, value)}</Tag>
}
