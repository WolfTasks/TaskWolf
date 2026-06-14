interface Props {
  x1: number; y1: number; x2: number; y2: number
  hasGuards: boolean
  onClick: () => void
}

export function TransitionArrow({ x1, y1, x2, y2, hasGuards, onClick }: Props) {
  const mx = (x1 + x2) / 2
  const my = (y1 + y2) / 2
  return (
    <g onClick={onClick} style={{ cursor: 'pointer' }}>
      <line x1={x1} y1={y1} x2={x2} y2={y2}
        stroke={hasGuards ? '#f59e0b' : '#6366f1'} strokeWidth={2} markerEnd="url(#arrow)" />
      <circle cx={mx} cy={my} r={8} fill={hasGuards ? '#f59e0b' : '#6366f1'} opacity={0.8} />
      <text x={mx} y={my + 4} textAnchor="middle" fontSize={10} fill="white">
        {hasGuards ? '🔒' : '+'}
      </text>
    </g>
  )
}
