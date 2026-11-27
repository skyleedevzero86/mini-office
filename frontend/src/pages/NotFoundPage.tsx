import { Link } from 'react-router'
import { Empty } from '@/components/ui'

export function NotFoundPage() {
  return <Empty>없는 화면입니다. <Link to="/">대시보드로</Link></Empty>
}
